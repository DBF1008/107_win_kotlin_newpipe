package org.schabi.newpipe.player.playqueue;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
public class PlayQueueTest {
    static PlayQueue makePlayQueue(final int index, final List<PlayQueueItem> streams) {
        // I tried using Mockito, but it didn't work for some reason
        return new PlayQueue(index, streams) {
            @Override
            public boolean isComplete() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void fetch() {
                throw new UnsupportedOperationException();
            }
        };
    }

    static PlayQueueItem makeItemWithUrl(final String url) {
        final StreamInfoItem infoItem = new StreamInfoItem(
                0, url, "", StreamType.VIDEO_STREAM
        );
        return new PlayQueueItem(infoItem);
    }

    public static class SetIndexTests {
        private static final int SIZE = 5;
        private PlayQueue nonEmptyQueue;
        private PlayQueue emptyQueue;

        @Before
        public void setup() {
            final List<PlayQueueItem> streams = new ArrayList<>(5);
            for (int i = 0; i < 5; ++i) {
                streams.add(makeItemWithUrl("URL_" + i));
            }
            nonEmptyQueue = spy(makePlayQueue(0, streams));
            emptyQueue = spy(makePlayQueue(0, new ArrayList<>()));
        }

        @Test
        public void negative() {
            nonEmptyQueue.setIndex(-5);
            assertEquals(0, nonEmptyQueue.getIndex());

            emptyQueue.setIndex(-5);
            assertEquals(0, nonEmptyQueue.getIndex());
        }

        @Test
        public void inBounds() {
            nonEmptyQueue.setIndex(2);
            assertEquals(2, nonEmptyQueue.getIndex());

            // emptyQueue not tested because 0 isn't technically inBounds
        }

        @Test
        public void outOfBoundIsComplete() {
            doReturn(true).when(nonEmptyQueue).isComplete();
            nonEmptyQueue.setIndex(7);
            assertEquals(2, nonEmptyQueue.getIndex());

            doReturn(true).when(emptyQueue).isComplete();
            emptyQueue.setIndex(2);
            assertEquals(0, emptyQueue.getIndex());
        }

        @Test
        public void outOfBoundsNotComplete() {
            doReturn(false).when(nonEmptyQueue).isComplete();
            nonEmptyQueue.setIndex(7);
            assertEquals(SIZE - 1, nonEmptyQueue.getIndex());

            doReturn(false).when(emptyQueue).isComplete();
            emptyQueue.setIndex(2);
            assertEquals(0, emptyQueue.getIndex());
        }

        @Test
        public void indexZero() {
            nonEmptyQueue.setIndex(0);
            assertEquals(0, nonEmptyQueue.getIndex());

            doReturn(true).when(emptyQueue).isComplete();
            emptyQueue.setIndex(0);
            assertEquals(0, emptyQueue.getIndex());

            doReturn(false).when(emptyQueue).isComplete();
            emptyQueue.setIndex(0);
            assertEquals(0, emptyQueue.getIndex());
        }

        @Test
        public void addToHistory() {
            nonEmptyQueue.setIndex(0);
            assertFalse(nonEmptyQueue.previous());

            nonEmptyQueue.setIndex(3);
            assertTrue(nonEmptyQueue.previous());
            assertEquals("URL_0", Objects.requireNonNull(nonEmptyQueue.getItem()).getUrl());
        }
    }

    public static class GetItemTests {
        private static List<PlayQueueItem> streams;
        private PlayQueue queue;

        @BeforeClass
        public static void init() {
            streams = new ArrayList<>(Collections.nCopies(5, makeItemWithUrl("OTHER_URL")));
            streams.set(3, makeItemWithUrl("TARGET_URL"));
        }

        @Before
        public void setup() {
            queue = makePlayQueue(0, streams);
        }

        @Test
        public void inBounds() {
            assertEquals("TARGET_URL", Objects.requireNonNull(queue.getItem(3)).getUrl());
            assertEquals("OTHER_URL", Objects.requireNonNull(queue.getItem(1)).getUrl());
        }

        @Test
        public void outOfBounds() {
            assertNull(queue.getItem(-1));
            assertNull(queue.getItem(5));
        }

        @Test
        public void itemsAreNotCloned() {
            final PlayQueueItem item = makeItemWithUrl("A url");
            final PlayQueue playQueue = makePlayQueue(0, List.of(item));

            // make sure that items are not cloned when added to the queue
            assertSame(playQueue.getItem(), item);
        }
    }

    public static class EqualsTests {
        private final PlayQueueItem item1 = makeItemWithUrl("URL_1");
        private final PlayQueueItem item2 = makeItemWithUrl("URL_2");

        @Test
        public void sameStreams() {
            final List<PlayQueueItem> streams = Collections.nCopies(5, item1);
            final PlayQueue queue1 = makePlayQueue(0, streams);
            final PlayQueue queue2 = makePlayQueue(0, streams);
            assertTrue(queue1.equalStreams(queue2));
            assertTrue(queue1.equalStreamsAndIndex(queue2));
        }

        @Test
        public void sameStreamsDifferentIndex() {
            final List<PlayQueueItem> streams = Collections.nCopies(5, item1);
            final PlayQueue queue1 = makePlayQueue(1, streams);
            final PlayQueue queue2 = makePlayQueue(4, streams);
            assertTrue(queue1.equalStreams(queue2));
            assertFalse(queue1.equalStreamsAndIndex(queue2));
        }

        @Test
        public void sameSizeDifferentItems() {
            final List<PlayQueueItem> streams1 = Collections.nCopies(5, item1);
            final List<PlayQueueItem> streams2 = Collections.nCopies(5, item2);
            final PlayQueue queue1 = makePlayQueue(0, streams1);
            final PlayQueue queue2 = makePlayQueue(0, streams2);
            assertFalse(queue1.equalStreams(queue2));
        }

        @Test
        public void differentSizeStreams() {
            final List<PlayQueueItem> streams1 = Collections.nCopies(5, item1);
            final List<PlayQueueItem> streams2 = Collections.nCopies(6, item2);
            final PlayQueue queue1 = makePlayQueue(0, streams1);
            final PlayQueue queue2 = makePlayQueue(0, streams2);
            assertFalse(queue1.equalStreams(queue2));
        }
    }

    public static class AppendTests {
        private PlayQueue queue;

        @Before
        public void setup() {
            final List<PlayQueueItem> streams = new ArrayList<>();
            streams.add(makeItemWithUrl("URL_0"));
            streams.add(makeItemWithUrl("URL_1"));
            queue = makePlayQueue(0, streams);
        }

        @Test
        public void appendEmptyListDoesNothing() {
            final int sizeBefore = queue.size();
            final int indexBefore = queue.getIndex();
            queue.append(List.of());
            assertEquals(sizeBefore, queue.size());
            assertEquals(indexBefore, queue.getIndex());
        }

        @Test
        public void appendAddsItemsToEnd() {
            final PlayQueueItem newItem = makeItemWithUrl("NEW_URL");
            queue.append(List.of(newItem));
            assertEquals(3, queue.size());
            assertSame(newItem, queue.getItem(2));
        }

        @Test
        public void appendReplacesAutoQueuedTail() {
            // Set up: last item is autoQueued
            final PlayQueueItem autoItem = makeItemWithUrl("AUTO");
            autoItem.setAutoQueued(true);
            queue.append(List.of(autoItem));
            assertEquals(3, queue.size());
            assertTrue(queue.getItem(2).isAutoQueued());

            // Append a non-autoQueued item — should replace the autoQueued tail
            final PlayQueueItem manualItem = makeItemWithUrl("MANUAL");
            queue.append(List.of(manualItem));
            assertEquals(3, queue.size());
            assertSame(manualItem, queue.getItem(2));
            assertFalse(queue.getItem(2).isAutoQueued());
        }

        @Test
        public void appendReplacesAutoQueuedTailFixesIndex() {
            // Queue: [URL_0, URL_1], index=0
            // Append autoQueued, then move index to the autoQueued item
            final PlayQueueItem autoItem = makeItemWithUrl("AUTO");
            autoItem.setAutoQueued(true);
            queue.append(List.of(autoItem));
            // Queue: [URL_0, URL_1, AUTO], index=0
            queue.setIndex(2);
            // Queue index = 2 (AUTO item)

            // Append non-autoQueued item — replaces AUTO
            final PlayQueueItem manualItem = makeItemWithUrl("MANUAL");
            queue.append(List.of(manualItem));

            // Index should still be valid and point to the new item
            assertTrue(queue.getIndex() < queue.size());
            assertSame(manualItem, queue.getItem(queue.getIndex()));
        }

        @Test
        public void appendReplacesAutoQueuedTailCleansHistory() {
            final PlayQueueItem autoItem = makeItemWithUrl("AUTO");
            autoItem.setAutoQueued(true);
            queue.append(List.of(autoItem));

            // Navigate to the autoQueued item to add it to history
            queue.setIndex(2);

            // Append non-autoQueued item — replaces AUTO
            final PlayQueueItem manualItem = makeItemWithUrl("MANUAL");
            queue.append(List.of(manualItem));

            // The autoQueued item should be gone from streams
            for (int i = 0; i < queue.size(); i++) {
                assertFalse("AutoQueued item should have been removed",
                        queue.getItem(i).isSameItem(autoItem));
            }
        }

        @Test
        public void appendDoesNotReplaceAutoQueuedIfNewIsAlsoAutoQueued() {
            final PlayQueueItem autoItem1 = makeItemWithUrl("AUTO1");
            autoItem1.setAutoQueued(true);
            queue.append(List.of(autoItem1));

            final PlayQueueItem autoItem2 = makeItemWithUrl("AUTO2");
            autoItem2.setAutoQueued(true);
            queue.append(List.of(autoItem2));

            // Both auto items should remain
            assertEquals(4, queue.size());
        }

        @Test
        public void appendOnEmptyQueue() {
            final PlayQueue emptyQueue = makePlayQueue(0, new ArrayList<>());
            final PlayQueueItem item = makeItemWithUrl("ITEM");
            emptyQueue.append(List.of(item));
            assertEquals(1, emptyQueue.size());
            assertSame(item, emptyQueue.getItem(0));
        }

        @Test
        public void appendOnShuffledQueue() {
            final List<PlayQueueItem> streams = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                streams.add(makeItemWithUrl("URL_" + i));
            }
            final PlayQueue shuffledQueue = makePlayQueue(0, streams);
            shuffledQueue.shuffle();
            assertTrue(shuffledQueue.isShuffled());

            final PlayQueueItem newItem = makeItemWithUrl("NEW");
            final int sizeBefore = shuffledQueue.size();
            shuffledQueue.append(List.of(newItem));
            assertEquals(sizeBefore + 1, shuffledQueue.size());
        }
    }

    public static class EnqueueNextTests {
        private PlayQueue queue;

        @Before
        public void setup() {
            final List<PlayQueueItem> streams = new ArrayList<>();
            streams.add(makeItemWithUrl("URL_0"));
            streams.add(makeItemWithUrl("URL_1"));
            streams.add(makeItemWithUrl("URL_2"));
            queue = makePlayQueue(0, streams);
        }

        @Test
        public void enqueueNextOnEmptyQueueDoesNothing() {
            final PlayQueue emptyQueue = makePlayQueue(0, new ArrayList<>());
            final PlayQueueItem item = makeItemWithUrl("ITEM");
            // Should not crash
            emptyQueue.enqueueNext(item, false);
            assertTrue(emptyQueue.isEmpty());
        }

        @Test
        public void enqueueNextInsertsAfterCurrent() {
            final PlayQueueItem newItem = makeItemWithUrl("NEXT");
            queue.enqueueNext(newItem, false);
            // Should be at index 1 (after current=0)
            assertEquals(4, queue.size());
            assertSame(newItem, queue.getItem(1));
        }

        @Test
        public void enqueueNextPreservesCurrentIndex() {
            queue.setIndex(1);
            final PlayQueueItem newItem = makeItemWithUrl("NEXT");
            queue.enqueueNext(newItem, false);
            assertEquals(1, queue.getIndex());
            assertSame(newItem, queue.getItem(2));
        }

        @Test
        public void enqueueNextSkipIfSame() {
            // Current is URL_0, next is URL_1
            final PlayQueueItem sameAsNext = makeItemWithUrl("URL_1");
            queue.enqueueNext(sameAsNext, true);
            // Should not add because the next item is the same URL
            assertEquals(3, queue.size());
        }

        @Test
        public void enqueueNextNoSkipIfDifferent() {
            final PlayQueueItem newItem = makeItemWithUrl("DIFFERENT");
            queue.enqueueNext(newItem, true);
            assertEquals(4, queue.size());
            assertSame(newItem, queue.getItem(1));
        }

        @Test
        public void enqueueNextPreservesAutoQueuedFlag() {
            final PlayQueueItem autoItem = makeItemWithUrl("AUTO");
            autoItem.setAutoQueued(true);
            queue.enqueueNext(autoItem, false);
            // The autoQueued flag should be preserved by enqueueNext
            assertTrue(queue.getItem(1).isAutoQueued());
        }

        @Test
        public void enqueueNextAfterAutoQueuedReplacement() {
            // Add autoQueued item at the end
            final PlayQueueItem autoItem = makeItemWithUrl("AUTO");
            autoItem.setAutoQueued(true);
            queue.append(List.of(autoItem));
            // Queue: [URL_0, URL_1, URL_2, AUTO]

            // enqueueNext should work correctly
            final PlayQueueItem nextItem = makeItemWithUrl("NEXT");
            queue.enqueueNext(nextItem, false);
            // Queue: [URL_0, NEXT, URL_1, URL_2, AUTO]
            assertEquals(5, queue.size());
            assertSame(nextItem, queue.getItem(1));
        }
    }

    public static class RemoveTests {
        @Test
        public void removeLastItemDoesNotCrash() {
            // Single item queue — previously caused division by zero
            final PlayQueueItem item = makeItemWithUrl("ONLY");
            final PlayQueue queue = makePlayQueue(0, new ArrayList<>(List.of(item)));
            assertEquals(1, queue.size());
            queue.remove(0);
            assertEquals(0, queue.size());
            assertEquals(0, queue.getIndex());
        }

        @Test
        public void removeBeforeCurrentDecrementsIndex() {
            final List<PlayQueueItem> streams = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                streams.add(makeItemWithUrl("URL_" + i));
            }
            final PlayQueue queue = makePlayQueue(3, streams);

            queue.remove(1);
            assertEquals(2, queue.getIndex());
            assertEquals(4, queue.size());
        }

        @Test
        public void removeAfterCurrentKeepsIndex() {
            final List<PlayQueueItem> streams = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                streams.add(makeItemWithUrl("URL_" + i));
            }
            final PlayQueue queue = makePlayQueue(1, streams);

            queue.remove(3);
            assertEquals(1, queue.getIndex());
            assertEquals(4, queue.size());
        }

        @Test
        public void removeCurrentLastItemWrapsToZero() {
            final List<PlayQueueItem> streams = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                streams.add(makeItemWithUrl("URL_" + i));
            }
            final PlayQueue queue = makePlayQueue(2, streams);

            queue.remove(2);
            assertEquals(0, queue.getIndex());
        }

        @Test
        public void removeOutOfBoundsDoesNothing() {
            final List<PlayQueueItem> streams = new ArrayList<>();
            streams.add(makeItemWithUrl("URL_0"));
            final PlayQueue queue = makePlayQueue(0, streams);

            queue.remove(-1);
            queue.remove(5);
            assertEquals(1, queue.size());
        }

        @Test
        public void removeAllItemsOneByOne() {
            final List<PlayQueueItem> streams = new ArrayList<>();
            streams.add(makeItemWithUrl("URL_0"));
            streams.add(makeItemWithUrl("URL_1"));
            final PlayQueue queue = makePlayQueue(0, streams);

            queue.remove(1);
            assertEquals(1, queue.size());
            queue.remove(0);
            assertEquals(0, queue.size());
            assertEquals(0, queue.getIndex());
        }
    }

    public static class MoveTests {
        @Test
        public void moveClearsAutoQueuedFlag() {
            final List<PlayQueueItem> streams = new ArrayList<>();
            streams.add(makeItemWithUrl("URL_0"));
            streams.add(makeItemWithUrl("URL_1"));
            final PlayQueue queue = makePlayQueue(0, streams);

            queue.getItem(1).setAutoQueued(true);
            assertTrue(queue.getItem(1).isAutoQueued());

            queue.move(1, 0);
            assertFalse(queue.getItem(0).isAutoQueued());
        }

        @Test
        public void moveOutOfBoundsDoesNothing() {
            final List<PlayQueueItem> streams = new ArrayList<>();
            streams.add(makeItemWithUrl("URL_0"));
            final PlayQueue queue = makePlayQueue(0, streams);

            queue.move(-1, 0);
            queue.move(0, 5);
            assertEquals(1, queue.size());
        }

        @Test
        public void moveSamePositionIsNoop() {
            final List<PlayQueueItem> streams = new ArrayList<>();
            streams.add(makeItemWithUrl("URL_0"));
            streams.add(makeItemWithUrl("URL_1"));
            final PlayQueue queue = makePlayQueue(0, streams);

            queue.move(1, 1);
            assertEquals("URL_1", queue.getItem(1).getUrl());
        }

        @Test
        public void moveCurrentItemUpdatesIndex() {
            final List<PlayQueueItem> streams = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                streams.add(makeItemWithUrl("URL_" + i));
            }
            final PlayQueue queue = makePlayQueue(0, streams);

            queue.move(0, 3);
            assertEquals(3, queue.getIndex());
        }
    }
}
