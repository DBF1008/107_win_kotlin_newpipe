package org.schabi.newpipe.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.reactivex.rxjava3.core.Single
import java.io.IOException
import java.time.OffsetDateTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.schabi.newpipe.database.feed.dao.FeedDAO
import org.schabi.newpipe.database.feed.model.FeedEntity
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.database.feed.model.FeedLastUpdatedEntity
import org.schabi.newpipe.database.history.model.StreamHistoryEntity
import org.schabi.newpipe.database.stream.StreamWithState
import org.schabi.newpipe.database.stream.dao.StreamDAO
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

    /**
     * Regression for defect A: a live stream must stay visible in the feed regardless of the
     * "show played" / "show partially played" filters. Previously the second filter block
     * (`includePartiallyPlayed`) lacked the live-stream exemption that the first block had, so a
     * live stream with a playback state would flicker in and out across refreshes as its duration
     * fluctuated. A regular video with the same mid-progress state is used as a control: it MUST be
     * filtered out when partially-played streams are hidden, proving only live streams are exempt.
     */
    @Test
    fun testGetStreams_liveStreamShownRegardlessOfPlaybackFilters() {
        db.clearAllTables()
        val subId = subscriptionDAO.insertAll(listOf(youtubeSub("1"))).first()

        val ids = streamDAO.insertAll(
            listOf(
                streamEntity(StreamType.LIVE_STREAM, 7200, "https://youtube.com/watch?v=live"),
                streamEntity(StreamType.VIDEO_STREAM, 7200, "https://youtube.com/watch?v=video")
            )
        )
        val liveId = ids[0]
        val videoId = ids[1]
        feedDAO.insertAll(listOf(FeedEntity(liveId, subId), FeedEntity(videoId, subId)))

        // Mid-progress playback state + history on both, so the played/partially-played filter
        // blocks are actually engaged (otherwise NULL state/history short-circuits to "keep").
        val now = OffsetDateTime.now()
        for (id in ids) {
            streamStateDAO.insert(StreamStateEntity(id, 3_600_000))
            streamHistoryDAO.insert(StreamHistoryEntity(id, now, 1))
        }

        val playedPartial = feedStreamUids(includePlayed = true, includePartiallyPlayed = true)
        val playedNoPartial = feedStreamUids(includePlayed = true, includePartiallyPlayed = false)
        val noPlayedPartial = feedStreamUids(includePlayed = false, includePartiallyPlayed = true)
        val noPlayedNoPartial = feedStreamUids(includePlayed = false, includePartiallyPlayed = false)

        // The live stream is present in every combination of the two playback filters.
        assertTrue(playedPartial.contains(liveId))
        assertTrue(playedNoPartial.contains(liveId))
        assertTrue(noPlayedPartial.contains(liveId))
        assertTrue(noPlayedNoPartial.contains(liveId))

        // Control: the regular video disappears whenever partially-played streams are hidden,
        // confirming the exemption is specific to live streams and normal filtering still works.
        assertTrue(playedPartial.contains(videoId))
        assertFalse(playedNoPartial.contains(videoId))
        assertFalse(noPlayedNoPartial.contains(videoId))
    }

    /**
     * Regression for defect C: the oldest-update query must return NULL when any subscription has
     * never been (successfully) updated, instead of silently ignoring NULLs via `MIN()`. This keeps
     * the "last updated" header honest about failures and consistent with [FeedDAO.notLoadedCount].
     */
    @Test
    fun testOldestSubscriptionUpdate_nullWhenAnyNotUpdated() {
        db.clearAllTables()
        val ids = subscriptionDAO.insertAll(
            listOf(youtubeSub("1"), youtubeSub("2"), youtubeSub("3"))
        )

        val t1 = OffsetDateTime.parse("2023-01-01T00:00:00Z")
        val t2 = OffsetDateTime.parse("2023-02-01T00:00:00Z")
        feedDAO.setLastUpdatedForSubscription(FeedLastUpdatedEntity(ids[0], t1))
        feedDAO.setLastUpdatedForSubscription(FeedLastUpdatedEntity(ids[1], t2))
        feedDAO.setLastUpdatedForSubscription(FeedLastUpdatedEntity(ids[2], null))

        // One subscription is not updated → the whole result must collapse to NULL.
        assertNull(feedDAO.oldestSubscriptionUpdateFromAll().blockingFirst().firstOrNull())

        // Once every subscription has a timestamp, the oldest one is returned again.
        feedDAO.setLastUpdatedForSubscription(
            FeedLastUpdatedEntity(ids[2], OffsetDateTime.parse("2023-03-01T00:00:00Z"))
        )
        assertEquals(t1, feedDAO.oldestSubscriptionUpdateFromAll().blockingFirst().firstOrNull())
    }

    /**
     * Regression for defects C/D: after a subscription is marked outdated (last_updated = NULL,
     * as the load pipeline does on failure), all three views of its state must agree — it is
     * reported as outdated, counted as not-loaded, and forces the oldest-update result to NULL.
     */
    @Test
    fun testFailedSubscription_consistentAcrossOutdatedCountAndOldest() {
        db.clearAllTables()
        val subId = subscriptionDAO.insertAll(listOf(youtubeSub("1"))).first()

        // Simulate a successful update followed by a failure (markAsOutdated → NULL).
        feedDAO.setLastUpdatedForSubscription(FeedLastUpdatedEntity(subId, OffsetDateTime.now()))
        feedDAO.setLastUpdatedForSubscription(FeedLastUpdatedEntity(subId, null))

        assertEquals(1, feedDAO.getAllOutdated(OffsetDateTime.now()).blockingFirst().size)
        assertEquals(1L, feedDAO.notLoadedCount().blockingFirst())
        assertNull(feedDAO.oldestSubscriptionUpdateFromAll().blockingFirst().firstOrNull())
    }

    private fun feedStreamUids(
        includePlayed: Boolean,
        includePartiallyPlayed: Boolean
    ): Set<Long> {
        return feedDAO.getStreams(
            FeedGroupEntity.GROUP_ALL_ID,
            includePlayed,
            includePartiallyPlayed,
            null
        )
            .blockingGet()!!
            .map { it.stream.uid }
            .toSet()
    }

    private fun youtubeSub(id: String) = SubscriptionEntity.from(
        ChannelInfo(
            serviceId,
            id,
            "https://youtube.com/channel/$id",
            "https://youtube.com/channel/$id",
            "channel-$id"
        )
    )

    private fun streamEntity(type: StreamType, durationSeconds: Long, url: String) = StreamEntity(
        0,
        serviceId,
        url,
        "title",
        type,
        durationSeconds,
        "uploader",
        "https://youtube.com/channel/x",
        "https://i.ytimg.com/vi/x/hqdefault.jpg",
        100,
        "2023-01-01",
        OffsetDateTime.parse("2023-01-01T00:00:00Z")
    )
}
