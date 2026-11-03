package dk.dtu.routeplanner.core.algorithms.duan;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import dk.dtu.routeplanner.core.algorithms.duan.Duan.Entry;
import dk.dtu.routeplanner.core.algorithms.duan.DuanQueue.Item;

class DuanQueueTest {
    @Test
    void exactKeysKeepTheirOrderWhenDoubleValuesTieOrOverflow() {
        BigDecimal large = new BigDecimal(1e20);
        BigDecimal overflowing = new BigDecimal(Double.MAX_VALUE).multiply(BigDecimal.valueOf(2));
        Entry smaller = new Entry(0, large.doubleValue(), 3, large);
        Entry larger = new Entry(1, large.doubleValue(), 1, large.add(BigDecimal.ONE));
        Entry finite = new Entry(2, overflowing.doubleValue(), 1, overflowing);
        DuanQueue queue = new DuanQueue(1, Duan.INFINITY, new Item[3]);
        queue.insert(finite);
        queue.insert(larger);
        queue.insert(smaller);
        DuanQueue.Batch first = queue.pull();
        assertEquals(List.of(0), first.sources());
        assertEquals(larger, first.bound());
        assertEquals(List.of(1), queue.pull().sources());
        assertEquals(List.of(2), queue.pull().sources());
        assertTrue(finite.compareTo(Duan.INFINITY) < 0);
    }

    @Test
    void mixedOperationsMatchASortedReference() {
        for (int batchSize : new int[]{1, 3, 4, 16}) {
            Random random = new Random(2026 + batchSize);
            Item[] index = new Item[80];
            Entry bound = new Entry(80, 200, 0);
            DuanQueue queue = new DuanQueue(batchSize, bound, index);
            Map<Integer, Entry> expected = new HashMap<>();
            for (int step = 0; step < 300; step++) {
                String context = "batchSize=" + batchSize + ", step=" + step;
                int operation = random.nextInt(5);
                if (operation < 3) {
                    Entry value = new Entry(random.nextInt(index.length), random.nextInt(100),
                            random.nextInt(4));
                    queue.insert(value);
                    keepBest(expected, value);
                } else if (operation == 3) {
                    double lower = expected.values().stream().mapToDouble(Entry::dist).min().orElse(0) - 1;
                    List<Entry> batch = new ArrayList<>();
                    int count = 1 + random.nextInt(3 * batchSize);
                    for (int i = 0; i < count; i++) {
                        Entry value = new Entry(random.nextInt(index.length), lower - random.nextInt(10),
                                random.nextInt(4));
                        batch.add(value);
                        keepBest(expected, value);
                    }
                    queue.batchPrepend(batch);
                } else {
                    checkPull(queue, expected, batchSize, bound, context);
                }
                assertEquals(expected.isEmpty(), queue.isEmpty(), context);
                for (int node = 0; node < index.length; node++) {
                    assertEquals(expected.get(node), index[node] == null ? null : index[node].value,
                            context + ", node=" + node);
                }
            }
            while (!queue.isEmpty()) {
                checkPull(queue, expected, batchSize, bound, "draining batchSize=" + batchSize);
            }
            assertTrue(expected.isEmpty(), "batchSize=" + batchSize);
            checkPull(queue, expected, batchSize, bound, "empty batchSize=" + batchSize);
        }
    }

    @Test
    void updatesKeysAndOrdersEqualDistancesByHopsThenNode() {
        Item[] index = new Item[8];
        DuanQueue queue = new DuanQueue(2, Duan.INFINITY, index);
        queue.insert(new Entry(0, 50, 2));
        queue.insert(new Entry(1, 50, 2));
        queue.batchPrepend(List.of(new Entry(0, 10, 2), new Entry(0, 5, 2),
                new Entry(0, 8, 2), new Entry(2, 5, 2), new Entry(3, 5, 1)));
        assertEquals(new Entry(0, 5, 2), index[0].value);
        assertEquals(Set.of(3, 0), new HashSet<>(queue.pull().sources()));
        assertEquals(Set.of(2, 1), new HashSet<>(queue.pull().sources()));
        assertTrue(queue.isEmpty());
    }

    @Test
    void clearAllowsTheIndexToBeReused() {
        Item[] index = new Item[20];
        DuanQueue queue = new DuanQueue(4, Duan.INFINITY, index);
        for (int node = 0; node < index.length; node++) {
            queue.insert(new Entry(node, 100 + node, 1));
        }
        queue.batchPrepend(List.of(new Entry(0, 1, 1), new Entry(1, 2, 1),
                new Entry(2, 3, 1), new Entry(3, 4, 1), new Entry(4, 5, 1), new Entry(5, 6, 1)));
        // Leave both block sequences nonempty when clearing the reusable index.
        queue.pull();
        queue.clear();
        assertTrue(queue.isEmpty());
        for (Item item : index) {
            assertNull(item);
        }
        DuanQueue nextCall = new DuanQueue(1, Duan.INFINITY, index);
        nextCall.insert(new Entry(5, 7, 1));
        assertEquals(List.of(5), nextCall.pull().sources());
        assertTrue(nextCall.isEmpty());
    }

    private static void keepBest(Map<Integer, Entry> expected, Entry value) {
        Entry old = expected.get(value.node());
        if (old == null || value.compareTo(old) < 0) {
            expected.put(value.node(), value);
        }
    }

    private static void checkPull(DuanQueue queue, Map<Integer, Entry> expected,
            int batchSize, Entry bound, String context) {
        List<Entry> ordered = new ArrayList<>(expected.values());
        ordered.sort(Comparator.naturalOrder());
        int count = Math.min(batchSize, ordered.size());
        Set<Integer> first = new HashSet<>();
        for (int i = 0; i < count; i++) {
            first.add(ordered.get(i).node());
        }
        DuanQueue.Batch result = queue.pull();
        assertEquals(count, result.sources().size(), context);
        assertEquals(first, new HashSet<>(result.sources()), context);
        for (int node : result.sources()) {
            expected.remove(node);
        }
        if (expected.isEmpty()) {
            assertEquals(bound, result.bound(), context);
        } else {
            assertTrue(ordered.get(count - 1).compareTo(result.bound()) < 0, context);
            assertTrue(result.bound().compareTo(ordered.get(count)) <= 0, context);
        }
    }
}
