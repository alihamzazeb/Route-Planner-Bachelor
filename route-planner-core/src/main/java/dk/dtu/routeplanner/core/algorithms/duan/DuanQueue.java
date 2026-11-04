package dk.dtu.routeplanner.core.algorithms.duan;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import dk.dtu.routeplanner.core.algorithms.duan.Duan.Entry;

// Lemma 3.3: D0 holds prepended blocks; D1 holds inserted blocks indexed by upper bound.
final class DuanQueue {
    record Batch(Entry bound, List<Integer> sources) {}

    static final class Item {
        final Entry value;
        Item previous;
        Item next;
        Block block;

        Item(Entry value) {
            this.value = value;
        }
    }

    private static final class Block {
        final Entry upperBound;
        Block previous;
        Block next;
        Item first;
        int size;

        Block(Entry upperBound) {
            this.upperBound = upperBound;
        }

        void add(Item item) {
            item.block = this;
            item.previous = null;
            item.next = first;
            if (first != null) {
                first.previous = item;
            }
            first = item;
            size++;
        }
    }

    private final int batchSize;
    private final Entry bound;
    private final Item[] entries;
    private final TreeMap<Entry, Block> index = new TreeMap<>();
    private Block prepended;
    private Block inserted;
    private Block lastInserted;

    DuanQueue(int batchSize, Entry bound, Item[] entries) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("Batch size must be positive");
        }
        this.batchSize = batchSize;
        this.bound = bound;
        this.entries = entries;
    }

    void insert(Entry value) {
        Item old = entries[value.node()];
        if (old != null) {
            if (value.compareTo(old.value) >= 0) {
                return;
            }
            remove(old);
        }
        Map.Entry<Entry, Block> location = index.ceilingEntry(value);
        Block block;
        if (location == null) {
            block = new Block(bound);
            block.previous = lastInserted;
            if (lastInserted == null) {
                inserted = block;
            } else {
                lastInserted.next = block;
            }
            lastInserted = block;
            index.put(bound, block);
        } else {
            block = location.getValue();
        }
        Item item = new Item(value);
        entries[value.node()] = item;
        block.add(item);
        if (block.size > batchSize) {
            split(block);
        }
    }

    void batchPrepend(List<Entry> values) {
        // everything in here has to be smaller than what is already in the queue
        List<Item> pending = new ArrayList<>();
        for (Entry value : values) {
            Item old = entries[value.node()];
            if (old != null) {
                if (value.compareTo(old.value) >= 0) {
                    continue;
                }
                if (old.block != null) {
                    remove(old);
                }
            }
            Item item = new Item(value);
            entries[value.node()] = item;
            pending.add(item);
        }
        // A later duplicate may have replaced an item from this same batch.
        pending.removeIf(item -> entries[item.value.node()] != item);
        Item[] items = pending.toArray(Item[]::new);
        prependBlocks(items, 0, items.length);
    }

    Batch pull() {
        List<Item> candidates = new ArrayList<>();
        collectPrefix(prepended, candidates);
        collectPrefix(inserted, candidates);
        Item[] items = candidates.toArray(new Item[0]);
        int count = Math.min(batchSize, items.length);
        if (count < items.length) {
            select(items, 0, items.length, count);
        }
        List<Integer> sources = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            sources.add(items[i].value.node());
            remove(items[i]);
        }
        Entry nextBound = minimum(prepended, minimum(inserted, bound));
        return new Batch(nextBound, sources);
    }

    boolean isEmpty() {
        return prepended == null && inserted == null;
    }

    void clear() {
        clearEntries(prepended);
        clearEntries(inserted);
        prepended = null;
        inserted = null;
        lastInserted = null;
        index.clear();
    }

    private void clearEntries(Block first) {
        for (Block block = first; block != null; block = block.next) {
            for (Item item = block.first; item != null; item = item.next) {
                entries[item.value.node()] = null;
            }
        }
    }

    private void collectPrefix(Block first, List<Item> result) {
        int count = 0;
        for (Block block = first; block != null && count < batchSize; block = block.next) {
            for (Item item = block.first; item != null; item = item.next) {
                result.add(item);
                count++;
            }
        }
    }

    private Entry minimum(Block block, Entry best) {
        if (block != null) {
            for (Item item = block.first; item != null; item = item.next) {
                if (item.value.compareTo(best) < 0) {
                    best = item.value;
                }
            }
        }
        return best;
    }

    private void remove(Item item) {
        Block block = item.block;
        if (item.previous == null) {
            block.first = item.next;
        } else {
            item.previous.next = item.next;
        }
        if (item.next != null) {
            item.next.previous = item.previous;
        }
        entries[item.value.node()] = null;
        item.block = null;
        if (--block.size != 0) {
            return;
        }
        if (block.previous != null) {
            block.previous.next = block.next;
        } else if (block.upperBound == null) {
            prepended = block.next;
        } else {
            inserted = block.next;
        }
        if (block.next != null) {
            block.next.previous = block.previous;
        } else if (block.upperBound != null) {
            lastInserted = block.previous;
        }
        if (block.upperBound != null) {
            index.remove(block.upperBound);
        }
    }

    private void split(Block block) {
        Item[] items = new Item[block.size];
        int count = 0;
        for (Item item = block.first; item != null; item = item.next) {
            items[count++] = item;
        }
        int middle = count / 2;
        select(items, 0, count, middle);
        Entry lowerBound = items[0].value;
        for (int i = 1; i < middle; i++) {
            if (items[i].value.compareTo(lowerBound) > 0) {
                lowerBound = items[i].value;
            }
        }
        Block lower = new Block(lowerBound);
        lower.previous = block.previous;
        lower.next = block;
        if (block.previous == null) {
            inserted = lower;
        } else {
            block.previous.next = lower;
        }
        block.previous = lower;
        block.first = null;
        block.size = 0;
        for (int i = 0; i < count; i++) {
            (i < middle ? lower : block).add(items[i]);
        }
        index.put(lowerBound, lower);
    }

    private void prependBlocks(Item[] items, int from, int to) {
        if (from == to) {
            return;
        }
        if (to - from > batchSize) {
            int middle = from + (to - from) / 2;
            select(items, from, to, middle);
            prependBlocks(items, middle, to);
            prependBlocks(items, from, middle);
            return;
        }
        Block block = new Block(null);
        for (int i = from; i < to; i++) {
            block.add(items[i]);
        }
        block.next = prepended;
        if (prepended != null) {
            prepended.previous = block;
        }
        prepended = block;
    }

    // Median-of-medians keeps selection linear; sorting the whole batch would add a log factor.
    static void select(Item[] items, int from, int to, int rank) {
        while (to - from > 5) {
            int medians = 0;
            for (int start = from; start < to; start += 5) {
                int end = Math.min(start + 5, to);
                sortSmall(items, start, end);
                swap(items, from + medians++, start + (end - start) / 2);
            }
            int pivotPosition = from + medians / 2;
            select(items, from, from + medians, pivotPosition);
            Entry pivot = items[pivotPosition].value;
            int left = from;
            int current = from;
            int right = to;
            while (current < right) {
                int comparison = items[current].value.compareTo(pivot);
                if (comparison < 0) {
                    swap(items, left++, current++);
                } else if (comparison > 0) {
                    swap(items, current, --right);
                } else {
                    current++;
                }
            }
            if (rank < left) {
                to = left;
            } else if (rank >= right) {
                from = right;
            } else {
                return;
            }
        }
        sortSmall(items, from, to);
    }

    private static void sortSmall(Item[] items, int from, int to) {
        for (int i = from + 1; i < to; i++) {
            Item value = items[i];
            int j = i;
            while (j > from && value.value.compareTo(items[j - 1].value) < 0) {
                items[j] = items[j - 1];
                j--;
            }
            items[j] = value;
        }
    }

    private static void swap(Item[] items, int first, int second) {
        Item value = items[first];
        items[first] = items[second];
        items[second] = value;
    }
}
