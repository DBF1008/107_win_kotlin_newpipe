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

    static PlayQueueItem makeAutoQueuedItemWithUrl(final String url) {
        final PlayQueueItem item = makeItemWithUrl(url);
        item.setAutoQueued(true);
        return item;
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
        @Test
        public void emptyAppendOnNonEmptyQueueIsNoOp() {
            final PlayQueue queue = makePlayQueue(1, List.of(
                    makeItemWithUrl("URL_0"),
                    makeItemWithUrl("URL_1"),
                    makeItemWithUrl("URL_2")));

            // Appending an empty result (e.g. an external enqueue/autoplay source
            // that returned nothing) must not crash and must leave the queue intact.
            queue.append(Collections.emptyList());

            assertEquals(3, queue.size());
            assertEquals(1, queue.getIndex());
            assertEquals("URL_1", Objects.requireNonNull(queue.getItem()).getUrl());
        }

        @Test
        public void emptyAppendOnEmptyQueueIsNoOp() {
            final PlayQueue queue = makePlayQueue(0, List.of());

            queue.append(Collections.emptyList());

            assertTrue(queue.isEmpty());
            assertEquals(0, queue.getIndex());
        }

        @Test
        public void appendReplacesTrailingAutoQueuedItem() {
            final PlayQueue queue = makePlayQueue(0, List.of(
                    makeItemWithUrl("PLAYING"),
                    makeAutoQueuedItemWithUrl("AUTO")));

            queue.append(List.of(makeItemWithUrl("REAL")));

            // The stale autoplay item must be dropped and replaced by the real item.
            assertEquals(2, queue.size());
            assertEquals("PLAYING", Objects.requireNonNull(queue.getItem(0)).getUrl());
            assertEquals("REAL", Objects.requireNonNull(queue.getItem(1)).getUrl());
            // The currently playing item is in front, so the index must stay put.
            assertEquals(0, queue.getIndex());
        }

        @Test
        public void replacingPlayingAutoQueuedItemFixesIndex() {
            final PlayQueue queue = makePlayQueue(0, List.of(
                    makeItemWithUrl("FIRST"),
                    makeAutoQueuedItemWithUrl("AUTO")));
            // Start playing the auto-queued item.
            queue.setIndex(1);
            assertEquals("AUTO", Objects.requireNonNull(queue.getItem()).getUrl());

            queue.append(List.of(makeItemWithUrl("REAL")));

            // The auto-queued item that was playing got removed; the index must
            // point to a valid item instead of dangling on the replacement / out
            // of bounds, and the queue must not be left in a dirty state.
            assertEquals(2, queue.size());
            assertEquals(0, queue.getIndex());
            assertEquals("FIRST", Objects.requireNonNull(queue.getItem()).getUrl());
            assertEquals("REAL", Objects.requireNonNull(queue.getItem(1)).getUrl());
        }

        @Test
        public void appendKeepsAutoQueuedWhenNewItemsAreAlsoAutoQueued() {
            final PlayQueue queue = makePlayQueue(0, List.of(
                    makeItemWithUrl("PLAYING"),
                    makeAutoQueuedItemWithUrl("AUTO_1")));

            queue.append(List.of(makeAutoQueuedItemWithUrl("AUTO_2")));

            // Both trailing items are auto-queued, so nothing should be dropped.
            assertEquals(3, queue.size());
            assertEquals("AUTO_1", Objects.requireNonNull(queue.getItem(1)).getUrl());
            assertEquals("AUTO_2", Objects.requireNonNull(queue.getItem(2)).getUrl());
        }
    }

    public static class EnqueueNextTests {
        @Test
        public void enqueueNextInsertsRightAfterCurrent() {
            final PlayQueue queue = makePlayQueue(0, List.of(
                    makeItemWithUrl("A"),
                    makeItemWithUrl("B"),
                    makeItemWithUrl("C")));

            queue.enqueueNext(makeItemWithUrl("NEXT"), false);

            assertEquals(4, queue.size());
            assertEquals(0, queue.getIndex());
            assertEquals("NEXT", Objects.requireNonNull(queue.getItem(1)).getUrl());
            assertEquals("B", Objects.requireNonNull(queue.getItem(2)).getUrl());
        }

        @Test
        public void enqueueNextSkipsWhenNextIsSameItem() {
            final PlayQueue queue = makePlayQueue(0, List.of(
                    makeItemWithUrl("A"),
                    makeItemWithUrl("DUP")));

            // The item right after the current one is already "DUP"; with skipIfSame
            // set the enqueue must be a no-op rather than duplicating the entry.
            queue.enqueueNext(makeItemWithUrl("DUP"), true);

            assertEquals(2, queue.size());
        }

        @Test
        public void enqueueNextOnEmptyQueueDoesNotCrash() {
            final PlayQueue queue = makePlayQueue(0, List.of());

            queue.enqueueNext(makeItemWithUrl("ONLY"), false);

            assertEquals(1, queue.size());
            assertEquals("ONLY", Objects.requireNonNull(queue.getItem(0)).getUrl());
        }
    }
}
