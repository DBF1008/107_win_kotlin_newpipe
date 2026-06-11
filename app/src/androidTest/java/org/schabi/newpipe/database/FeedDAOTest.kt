package org.schabi.newpipe.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.reactivex.rxjava3.core.Single
import java.io.IOException
import java.time.OffsetDateTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.schabi.newpipe.database.feed.dao.FeedDAO
import org.schabi.newpipe.database.feed.model.FeedEntity
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.database.feed.model.FeedLastUpdatedEntity
import org.schabi.newpipe.database.history.dao.StreamHistoryDAO
import org.schabi.newpipe.database.history.model.StreamHistoryEntity
import org.schabi.newpipe.database.stream.StreamWithState
import org.schabi.newpipe.database.stream.dao.StreamDAO
import org.schabi.newpipe.database.stream.dao.StreamStateDAO
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.database.stream.model.StreamStateEntity
import org.schabi.newpipe.database.subscription.SubscriptionDAO
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.stream.StreamType

class FeedDAOTest {
    private lateinit var db: AppDatabase
    private lateinit var feedDAO: FeedDAO
    private lateinit var streamDAO: StreamDAO
    private lateinit var subscriptionDAO: SubscriptionDAO
    private lateinit var streamStateDAO: StreamStateDAO
    private lateinit var streamHistoryDAO: StreamHistoryDAO

    private val serviceId = ServiceList.YouTube.serviceId

    private val stream1 = StreamEntity(1, serviceId, "https://youtube.com/watch?v=1", "stream 1", StreamType.VIDEO_STREAM, 1000, "channel-1", "https://youtube.com/channel/1", "https://i.ytimg.com/vi/1/hqdefault.jpg", 100, "2023-01-01", OffsetDateTime.parse("2023-01-01T00:00:00Z"))
    private val stream2 = StreamEntity(2, serviceId, "https://youtube.com/watch?v=2", "stream 2", StreamType.VIDEO_STREAM, 1000, "channel-1", "https://youtube.com/channel/1", "https://i.ytimg.com/vi/1/hqdefault.jpg", 100, "2023-01-02", OffsetDateTime.parse("2023-01-02T00:00:00Z"))
    private val stream3 = StreamEntity(3, serviceId, "https://youtube.com/watch?v=3", "stream 3", StreamType.LIVE_STREAM, 1000, "channel-1", "https://youtube.com/channel/1", "https://i.ytimg.com/vi/1/hqdefault.jpg", 100, "2023-01-03", OffsetDateTime.parse("2023-01-03T00:00:00Z"))
    private val stream4 = StreamEntity(4, serviceId, "https://youtube.com/watch?v=4", "stream 4", StreamType.VIDEO_STREAM, 1000, "channel-2", "https://youtube.com/channel/2", "https://i.ytimg.com/vi/1/hqdefault.jpg", 100, "2023-08-10", OffsetDateTime.parse("2023-08-10T00:00:00Z"))
    private val stream5 = StreamEntity(5, serviceId, "https://youtube.com/watch?v=5", "stream 5", StreamType.VIDEO_STREAM, 1000, "channel-2", "https://youtube.com/channel/2", "https://i.ytimg.com/vi/1/hqdefault.jpg", 100, "2023-08-20", OffsetDateTime.parse("2023-08-20T00:00:00Z"))
    private val stream6 = StreamEntity(6, serviceId, "https://youtube.com/watch?v=6", "stream 6", StreamType.VIDEO_STREAM, 1000, "channel-3", "https://youtube.com/channel/3", "https://i.ytimg.com/vi/1/hqdefault.jpg", 100, "2023-09-01", OffsetDateTime.parse("2023-09-01T00:00:00Z"))
    private val stream7 = StreamEntity(7, serviceId, "https://youtube.com/watch?v=7", "stream 7", StreamType.VIDEO_STREAM, 1000, "channel-4", "https://youtube.com/channel/4", "https://i.ytimg.com/vi/1/hqdefault.jpg", 100, "2023-08-10", OffsetDateTime.parse("2023-08-10T00:00:00Z"))

    private val allStreams = listOf(
        stream1,
        stream2,
        stream3,
        stream4,
        stream5,
        stream6,
        stream7
    )

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).build()
        feedDAO = db.feedDAO()
        streamDAO = db.streamDAO()
        subscriptionDAO = db.subscriptionDAO()
        streamStateDAO = db.streamStateDAO()
        streamHistoryDAO = db.streamHistoryDAO()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun testUnlinkStreamsOlderThan_KeepOne() {
        setupUnlinkDelete("2023-08-15T00:00:00Z")
        val streams = feedDAO.getStreams(
            FeedGroupEntity.GROUP_ALL_ID,
            includePlayed = true,
            includePartiallyPlayed = true,
            null
        )
            .blockingGet()
        val allowedStreams = listOf(stream3, stream5, stream6, stream7)
        assertEqual(streams, allowedStreams)
    }

    @Test
    fun testUnlinkStreamsOlderThan_KeepMultiple() {
        setupUnlinkDelete("2023-08-01T00:00:00Z")
        val streams = feedDAO.getStreams(
            FeedGroupEntity.GROUP_ALL_ID,
            includePlayed = true,
            includePartiallyPlayed = true,
            null
        )
            .blockingGet()
        val allowedStreams = listOf(stream3, stream4, stream5, stream6, stream7)
        assertEqual(streams, allowedStreams)
    }

    private fun assertEqual(streams: List<StreamWithState>?, allowedStreams: List<StreamEntity>) {
        assertNotNull(streams)
        assertEquals(
            allowedStreams,
            streams!!
                .map { it.stream }
                .sortedBy { it.uid }
                .toList()
        )
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Regression tests – feed refresh & filter chain
    // ──────────────────────────────────────────────────────────────────────

    /**
     * When includePlayed is false, fully-watched streams must be hidden
     * but live streams and never-played streams must remain visible.
     */
    @Test
    fun testGetStreams_HidesFullyPlayed_KeepsLiveAndUnwatched() {
        db.clearAllTables()

        // subscription 1 → channel-1
        val subId = subscriptionDAO.insert(
            SubscriptionEntity.from(
                ChannelInfo(serviceId, "1", "https://youtube.com/channel/1",
                    "https://youtube.com/channel/1", "channel-1")
            )
        )

        // watched video (100 s, progress at 95 s → finished)
        val watchedStream = StreamEntity(
            0, serviceId, "https://youtube.com/watch?v=watched", "watched",
            StreamType.VIDEO_STREAM, 100, "channel-1",
            "https://youtube.com/channel/1", null, 10,
            "2023-06-01", OffsetDateTime.parse("2023-06-01T00:00:00Z")
        )
        // unwatched video
        val freshStream = StreamEntity(
            0, serviceId, "https://youtube.com/watch?v=fresh", "fresh",
            StreamType.VIDEO_STREAM, 200, "channel-1",
            "https://youtube.com/channel/1", null, 10,
            "2023-06-02", OffsetDateTime.parse("2023-06-02T00:00:00Z")
        )
        // live stream (no upload_date)
        val liveStream = StreamEntity(
            0, serviceId, "https://youtube.com/watch?v=live", "live",
            StreamType.LIVE_STREAM, -1, "channel-1",
            "https://youtube.com/channel/1", null, 0,
            null, null
        )

        val watchedId = streamDAO.insert(watchedStream)
        val freshId = streamDAO.insert(freshStream)
        val liveId = streamDAO.insert(liveStream)

        feedDAO.insertAll(
            listOf(
                FeedEntity(watchedId, subId),
                FeedEntity(freshId, subId),
                FeedEntity(liveId, subId)
            )
        )

        // Mark the first stream as fully watched: progress 95 s of 100 s
        // (within PLAYBACK_FINISHED_END_MILLISECONDS of the end, and ≥ 3/4)
        streamHistoryDAO.insert(
            StreamHistoryEntity(
                watchedId,
                OffsetDateTime.parse("2023-06-01T01:00:00Z"),
                1
            )
        )
        streamStateDAO.upsert(StreamStateEntity(watchedId, 95_000L))

        val result = feedDAO.getStreams(
            FeedGroupEntity.GROUP_ALL_ID,
            includePlayed = false,
            includePartiallyPlayed = true,
            uploadDateBefore = null
        ).blockingGet()

        val resultIds = result.map { it.stream.uid }.toSet()
        assertTrue("Fresh stream should be visible", freshId in resultIds)
        assertTrue("Live stream should always be visible", liveId in resultIds)
        assertTrue("Fully-played stream should be hidden", watchedId !in resultIds)
    }

    /**
     * Streams with upload_date in the future must be hidden when
     * uploadDateBefore is set to now; streams without upload_date
     * (live streams) must remain visible.
     */
    @Test
    fun testGetStreams_FiltersFutureItems_KeepsLive() {
        db.clearAllTables()

        val subId = subscriptionDAO.insert(
            SubscriptionEntity.from(
                ChannelInfo(serviceId, "1", "https://youtube.com/channel/1",
                    "https://youtube.com/channel/1", "channel-1")
            )
        )

        val pastStream = StreamEntity(
            0, serviceId, "https://youtube.com/watch?v=past", "past",
            StreamType.VIDEO_STREAM, 100, "channel-1",
            "https://youtube.com/channel/1", null, 10,
            "2023-01-01", OffsetDateTime.parse("2023-01-01T00:00:00Z")
        )
        val futureStream = StreamEntity(
            0, serviceId, "https://youtube.com/watch?v=future", "future",
            StreamType.VIDEO_STREAM, 100, "channel-1",
            "https://youtube.com/channel/1", null, 10,
            "2099-01-01", OffsetDateTime.parse("2099-01-01T00:00:00Z")
        )
        val liveStream = StreamEntity(
            0, serviceId, "https://youtube.com/watch?v=live2", "live2",
            StreamType.LIVE_STREAM, -1, "channel-1",
            "https://youtube.com/channel/1", null, 0,
            null, null
        )

        val pastId = streamDAO.insert(pastStream)
        val futureId = streamDAO.insert(futureStream)
        val liveId = streamDAO.insert(liveStream)

        feedDAO.insertAll(
            listOf(
                FeedEntity(pastId, subId),
                FeedEntity(futureId, subId),
                FeedEntity(liveId, subId)
            )
        )

        val result = feedDAO.getStreams(
            FeedGroupEntity.GROUP_ALL_ID,
            includePlayed = true,
            includePartiallyPlayed = true,
            uploadDateBefore = OffsetDateTime.now()
        ).blockingGet()

        val resultIds = result.map { it.stream.uid }.toSet()
        assertTrue("Past stream should be visible", pastId in resultIds)
        assertTrue("Future stream should be hidden", futureId !in resultIds)
        assertTrue(
            "Live stream (null upload_date) should remain visible",
            liveId in resultIds
        )
    }

    /**
     * unlinkOldLivestreams must delete feed entries for live streams
     * but keep feed entries for regular (non-live) streams.
     */
    @Test
    fun testUnlinkOldLivestreams_OnlyRemovesLive() {
        db.clearAllTables()

        val subId = subscriptionDAO.insert(
            SubscriptionEntity.from(
                ChannelInfo(serviceId, "1", "https://youtube.com/channel/1",
                    "https://youtube.com/channel/1", "channel-1")
            )
        )

        val videoStream = StreamEntity(
            0, serviceId, "https://youtube.com/watch?v=vid1", "vid1",
            StreamType.VIDEO_STREAM, 100, "channel-1",
            "https://youtube.com/channel/1", null, 10,
            "2023-06-01", OffsetDateTime.parse("2023-06-01T00:00:00Z")
        )
        val liveStream = StreamEntity(
            0, serviceId, "https://youtube.com/watch?v=liveold", "liveold",
            StreamType.LIVE_STREAM, -1, "channel-1",
            "https://youtube.com/channel/1", null, 0,
            null, null
        )

        val videoId = streamDAO.insert(videoStream)
        val liveId = streamDAO.insert(liveStream)

        feedDAO.insertAll(
            listOf(FeedEntity(videoId, subId), FeedEntity(liveId, subId))
        )

        feedDAO.unlinkOldLivestreams(subId)

        val remaining = feedDAO.getStreams(
            FeedGroupEntity.GROUP_ALL_ID,
            includePlayed = true,
            includePartiallyPlayed = true,
            null
        ).blockingGet()

        assertEquals(1, remaining.size)
        assertEquals(videoId, remaining[0].stream.uid)
    }

    /**
     * Setting last_updated to null (marking a subscription as outdated)
     * must roundtrip correctly through insert + update.
     */
    @Test
    fun testSetLastUpdated_NullRoundtrip() {
        db.clearAllTables()

        val subId = subscriptionDAO.insert(
            SubscriptionEntity.from(
                ChannelInfo(serviceId, "1", "https://youtube.com/channel/1",
                    "https://youtube.com/channel/1", "channel-1")
            )
        )

        // First, set a valid timestamp
        feedDAO.setLastUpdatedForSubscription(
            FeedLastUpdatedEntity(subId, OffsetDateTime.now())
        )
        var count = feedDAO.notLoadedCount().blockingFirst()
        assertEquals("After setting timestamp, notLoadedCount should be 0", 0L, count)

        // Now mark as outdated (null)
        feedDAO.setLastUpdatedForSubscription(
            FeedLastUpdatedEntity(subId, null)
        )
        count = feedDAO.notLoadedCount().blockingFirst()
        assertEquals("After setting null, notLoadedCount should be 1", 1L, count)

        // Re-set a timestamp and verify it's no longer outdated
        feedDAO.setLastUpdatedForSubscription(
            FeedLastUpdatedEntity(subId, OffsetDateTime.now())
        )
        count = feedDAO.notLoadedCount().blockingFirst()
        assertEquals("After re-setting timestamp, notLoadedCount should be 0", 0L, count)
    }

    /**
     * upsertFeedItems must atomically:
     *  1. remove old live-stream feed entries for the subscription
     *  2. insert new feed entries
     *  3. set the last-updated timestamp
     *
     * After the call, old live streams should be gone, new ones present,
     * and the subscription should no longer be counted as "not loaded".
     */
    @Test
    fun testUpsertFeedItems_ReplacesLiveAndSetsTimestamp() {
        db.clearAllTables()

        val subId = subscriptionDAO.insert(
            SubscriptionEntity.from(
                ChannelInfo(serviceId, "1", "https://youtube.com/channel/1",
                    "https://youtube.com/channel/1", "channel-1")
            )
        )

        // Pre-populate: one video and one old live stream in feed
        val oldVideo = StreamEntity(
            0, serviceId, "https://youtube.com/watch?v=oldvid", "oldvid",
            StreamType.VIDEO_STREAM, 120, "channel-1",
            "https://youtube.com/channel/1", null, 50,
            "2023-06-01", OffsetDateTime.parse("2023-06-01T00:00:00Z")
        )
        val oldLive = StreamEntity(
            0, serviceId, "https://youtube.com/watch?v=oldlive", "oldlive",
            StreamType.LIVE_STREAM, -1, "channel-1",
            "https://youtube.com/channel/1", null, 0,
            null, null
        )

        val oldVideoId = streamDAO.insert(oldVideo)
        val oldLiveId = streamDAO.insert(oldLive)
        feedDAO.insertAll(
            listOf(FeedEntity(oldVideoId, subId), FeedEntity(oldLiveId, subId))
        )

        // Mark subscription as not loaded
        feedDAO.setLastUpdatedForSubscription(FeedLastUpdatedEntity(subId, null))
        assertEquals(1L, feedDAO.notLoadedCount().blockingFirst())

        // New items to upsert: a new live stream and the same old video
        val newLive = StreamEntity(
            0, serviceId, "https://youtube.com/watch?v=newlive", "newlive",
            StreamType.LIVE_STREAM, -1, "channel-1",
            "https://youtube.com/channel/1", null, 0,
            null, null
        )
        val newLiveId = streamDAO.insert(newLive)
        val newVideoId = oldVideoId // reuse the existing video

        val feedEntities = listOf(
            FeedEntity(newLiveId, subId),
            FeedEntity(newVideoId, subId)
        )

        feedDAO.upsertFeedItems(
            subId,
            feedEntities,
            FeedLastUpdatedEntity(subId, OffsetDateTime.now())
        )

        // Verify: old live stream feed entry removed
        val streams = feedDAO.getStreams(
            FeedGroupEntity.GROUP_ALL_ID,
            includePlayed = true,
            includePartiallyPlayed = true,
            null
        ).blockingGet()

        val streamIds = streams.map { it.stream.uid }.toSet()
        assertTrue("Old video should still be in feed", oldVideoId in streamIds)
        assertTrue("New live stream should be in feed", newLiveId in streamIds)
        assertTrue("Old live stream should be removed from feed", oldLiveId !in streamIds)

        // Verify: subscription is no longer marked as "not loaded"
        assertEquals(0L, feedDAO.notLoadedCount().blockingFirst())
    }

    /**
     * After upsertFeedItems with an empty list, old live streams
     * should still be removed and the subscription should be stamped
     * as refreshed — this is the "channel returned zero items" path.
     */
    @Test
    fun testUpsertFeedItems_EmptyList_ClearsOldLiveAndStamps() {
        db.clearAllTables()

        val subId = subscriptionDAO.insert(
            SubscriptionEntity.from(
                ChannelInfo(serviceId, "1", "https://youtube.com/channel/1",
                    "https://youtube.com/channel/1", "channel-1")
            )
        )

        val oldLive = StreamEntity(
            0, serviceId, "https://youtube.com/watch?v=stalelive", "stalelive",
            StreamType.LIVE_STREAM, -1, "channel-1",
            "https://youtube.com/channel/1", null, 0,
            null, null
        )
        val oldLiveId = streamDAO.insert(oldLive)
        feedDAO.insert(FeedEntity(oldLiveId, subId))
        feedDAO.setLastUpdatedForSubscription(FeedLastUpdatedEntity(subId, null))

        feedDAO.upsertFeedItems(
            subId,
            emptyList(),
            FeedLastUpdatedEntity(subId, OffsetDateTime.now())
        )

        val streams = feedDAO.getStreams(
            FeedGroupEntity.GROUP_ALL_ID,
            includePlayed = true,
            includePartiallyPlayed = true,
            null
        ).blockingGet()

        assertTrue("Old live stream should be removed", streams.isEmpty())
        assertEquals("Subscription should be stamped as loaded", 0L, feedDAO.notLoadedCount().blockingFirst())
    }

    /**
     * When includePartiallyPlayed is false, streams that are partially
     * watched (progress > 5 s and > 1/4 duration but not finished) must
     * be hidden. Unwatched and fully watched streams should follow their
     * respective filter rules.
     */
    @Test
    fun testGetStreams_HidesPartiallyPlayed_KeepsUnwatched() {
        db.clearAllTables()

        val subId = subscriptionDAO.insert(
            SubscriptionEntity.from(
                ChannelInfo(serviceId, "1", "https://youtube.com/channel/1",
                    "https://youtube.com/channel/1", "channel-1")
            )
        )

        // 200 s video, watched 100 s → partially played (> 5 s and > 1/4, not finished)
        val partialStream = StreamEntity(
            0, serviceId, "https://youtube.com/watch?v=partial", "partial",
            StreamType.VIDEO_STREAM, 200, "channel-1",
            "https://youtube.com/channel/1", null, 10,
            "2023-06-01", OffsetDateTime.parse("2023-06-01T00:00:00Z")
        )
        // Never watched
        val unwatchedStream = StreamEntity(
            0, serviceId, "https://youtube.com/watch?v=unwatched", "unwatched",
            StreamType.VIDEO_STREAM, 300, "channel-1",
            "https://youtube.com/channel/1", null, 10,
            "2023-06-02", OffsetDateTime.parse("2023-06-02T00:00:00Z")
        )

        val partialId = streamDAO.insert(partialStream)
        val unwatchedId = streamDAO.insert(unwatchedStream)

        feedDAO.insertAll(
            listOf(FeedEntity(partialId, subId), FeedEntity(unwatchedId, subId))
        )

        // Mark partial stream as partially watched (100 s of 200 s)
        streamHistoryDAO.insert(
            StreamHistoryEntity(
                partialId,
                OffsetDateTime.parse("2023-06-01T01:00:00Z"),
                1
            )
        )
        streamStateDAO.upsert(StreamStateEntity(partialId, 100_000L))

        val result = feedDAO.getStreams(
            FeedGroupEntity.GROUP_ALL_ID,
            includePlayed = true,
            includePartiallyPlayed = false,
            uploadDateBefore = null
        ).blockingGet()

        val resultIds = result.map { it.stream.uid }.toSet()
        assertTrue("Unwatched stream should be visible", unwatchedId in resultIds)
        assertTrue("Partially played stream should be hidden", partialId !in resultIds)
    }

    /**
     * Verifies that the oldestSubscriptionUpdate query returns null
     * when any subscription in the group has last_updated = null,
     * ensuring the UI correctly shows the "needs refresh" indicator.
     */
    @Test
    fun testOldestSubscriptionUpdate_ReturnsNullWhenAnyOutdated() {
        db.clearAllTables()

        val sub1 = subscriptionDAO.insert(
            SubscriptionEntity.from(
                ChannelInfo(serviceId, "1", "https://youtube.com/channel/1",
                    "https://youtube.com/channel/1", "channel-1")
            )
        )
        val sub2 = subscriptionDAO.insert(
            SubscriptionEntity.from(
                ChannelInfo(serviceId, "2", "https://youtube.com/channel/2",
                    "https://youtube.com/channel/2", "channel-2")
            )
        )

        val now = OffsetDateTime.now()
        feedDAO.setLastUpdatedForSubscription(FeedLastUpdatedEntity(sub1, now))
        feedDAO.setLastUpdatedForSubscription(FeedLastUpdatedEntity(sub2, now))

        // Both loaded — oldest should be non-null
        val loaded = feedDAO.oldestSubscriptionUpdateFromAll().blockingFirst()
        assertNotNull("Should have an oldest-update when all are loaded", loaded.firstOrNull())

        // Mark sub2 as outdated
        feedDAO.setLastUpdatedForSubscription(FeedLastUpdatedEntity(sub2, null))

        val outdated = feedDAO.oldestSubscriptionUpdateFromAll().blockingFirst()
        assertNull(
            "oldestSubscriptionUpdate should be null when any sub is outdated",
            outdated.firstOrNull()
        )
    }

    private fun setupUnlinkDelete(time: String) {
        clearAndFillTables()
        Single.fromCallable {
            feedDAO.unlinkStreamsOlderThan(OffsetDateTime.parse(time))
        }.blockingSubscribe()
        Single.fromCallable {
            streamDAO.deleteOrphans()
        }.blockingSubscribe()
    }

    private fun clearAndFillTables() {
        db.clearAllTables()
        streamDAO.insertAll(allStreams)
        subscriptionDAO.insertAll(
            listOf(
                SubscriptionEntity.from(ChannelInfo(serviceId, "1", "https://youtube.com/channel/1", "https://youtube.com/channel/1", "channel-1")),
                SubscriptionEntity.from(ChannelInfo(serviceId, "2", "https://youtube.com/channel/2", "https://youtube.com/channel/2", "channel-2")),
                SubscriptionEntity.from(ChannelInfo(serviceId, "3", "https://youtube.com/channel/3", "https://youtube.com/channel/3", "channel-3")),
                SubscriptionEntity.from(ChannelInfo(serviceId, "4", "https://youtube.com/channel/4", "https://youtube.com/channel/4", "channel-4"))
            )
        )
        feedDAO.insertAll(
            listOf(
                FeedEntity(1, 1),
                FeedEntity(2, 1),
                FeedEntity(3, 1),
                FeedEntity(4, 2),
                FeedEntity(5, 2),
                FeedEntity(6, 3),
                FeedEntity(7, 4)
            )
        )
    }
}
