package dk.dtu.routeplanner.core.algorithms.duan;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import dk.dtu.routeplanner.core.algorithms.duan.Duan.Entry;
import dk.dtu.routeplanner.core.algorithms.duan.DuanQueue.Item;

class DuanQueueTest {
    @Test
    void batchSizeMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> new DuanQueue(0, Duan.INFINITY, new Item[1]));
    }

    @Test
    void mixedOperationsMatchASortedReference() {
        for (int batchSize : new int[]{1, 4, 16}) {
            Random random = new Random(2026 + batchSize);
            Item[] index = new Item[80];
            DuanQueue queue = new DuanQueue(batchSize, Duan.INFINITY, index);
            Map<Integer, Entry> expected = new HashMap<>();
            for (int step = 0; step < 300; step++) {
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
                    checkPull(queue, expected, batchSize, Duan.INFINITY);
                }
                assertEquals(expected.isEmpty(), queue.isEmpty());
                for (int node = 0; node < index.length; node++) {
                    assertEquals(expected.get(node), index[node] == null ? null : index[node].value);
                }
            }
            while (!queue.isEmpty()) {
                checkPull(queue, expected, batchSize, Duan.INFINITY);
            }
            assertTrue(expected.isEmpty());
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
    void returnsTheUpperBoundWhenTheQueueIsEmpty() {
        Entry bound = new Entry(100, 20, 0);
        DuanQueue queue = new DuanQueue(3, bound, new Item[10]);
        Map<Integer, Entry> expected = new HashMap<>();
        for (int node = 0; node < 10; node++) {
            Entry value = new Entry(node, node, 1);
            queue.insert(value);
            expected.put(node, value);
        }
        while (!queue.isEmpty()) {
            checkPull(queue, expected, 3, bound);
        }
        assertEquals(bound, queue.pull().bound());
    }

    @Test
    void clearAllowsTheIndexToBeReused() {
        Item[] index = new Item[100];
        DuanQueue queue = new DuanQueue(4, Duan.INFINITY, index);
        for (int node = 0; node < index.length; node++) {
            queue.insert(new Entry(node, 100 + node, 1));
        }
        queue.batchPrepend(List.of(new Entry(10, 1, 1), new Entry(30, 2, 1)));
        queue.pull();
        queue.clear();
        assertTrue(queue.isEmpty());
        for (Item item : index) {
            assertNull(item);
        }
        DuanQueue nextCall = new DuanQueue(1, Duan.INFINITY, index);
        nextCall.insert(new Entry(30, 7, 1));
        assertEquals(List.of(30), nextCall.pull().sources());
        assertTrue(nextCall.isEmpty());
    }

    @Test
    void selectionMatchesSortingForDifferentOrders() {
        Random random = new Random(711);
        for (int size : new int[]{1, 5, 6, 31, 128}) {
            for (int pattern = 0; pattern < 5; pattern++) {
                Item[] input = new Item[size];
                for (int i = 0; i < size; i++) {
                    double value = switch (pattern) {
                        case 0 -> i;
                        case 1 -> size - i;
                        case 2 -> 0;
                        case 3 -> i % 7;
                        default -> random.nextDouble();
                    };
                    input[i] = new Item(new Entry(i, value, 0));
                }
                Item[] sorted = input.clone();
                Arrays.sort(sorted, Comparator.comparing(item -> item.value));
                for (int rank : new int[]{0, size / 2, size - 1}) {
                    Item[] actual = input.clone();
                    DuanQueue.select(actual, 0, size, rank);
                    assertEquals(sorted[rank].value, actual[rank].value);
                    for (int i = 0; i < rank; i++) {
                        assertTrue(actual[i].value.compareTo(actual[rank].value) < 0);
                    }
                    for (int i = rank + 1; i < size; i++) {
                        assertTrue(actual[i].value.compareTo(actual[rank].value) > 0);
                    }
                }
            }
        }
    }

    private static void keepBest(Map<Integer, Entry> expected, Entry value) {
        Entry old = expected.get(value.node());
        if (old == null || value.compareTo(old) < 0) {
            expected.put(value.node(), value);
        }
    }

    private static void checkPull(DuanQueue queue, Map<Integer, Entry> expected,
            int batchSize, Entry bound) {
        List<Entry> ordered = new ArrayList<>(expected.values());
        ordered.sort(Comparator.naturalOrder());
        int count = Math.min(batchSize, ordered.size());
        Set<Integer> first = new HashSet<>();
        for (int i = 0; i < count; i++) {
            first.add(ordered.get(i).node());
        }
        DuanQueue.Batch result = queue.pull();
        assertEquals(count, result.sources().size());
        assertEquals(first, new HashSet<>(result.sources()));
        for (int node : result.sources()) {
            expected.remove(node);
        }
        if (expected.isEmpty()) {
            assertEquals(bound, result.bound());
        } else {
            assertTrue(ordered.get(count - 1).compareTo(result.bound()) < 0);
            assertTrue(result.bound().compareTo(ordered.get(count)) <= 0);
        }
    }
}
