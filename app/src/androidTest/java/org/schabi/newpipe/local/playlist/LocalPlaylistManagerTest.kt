package org.schabi.newpipe.local.playlist

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.playlist.model.PlaylistEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.testUtil.TestDatabase
import org.schabi.newpipe.testUtil.TrampolineSchedulerRule

class LocalPlaylistManagerTest {

    private lateinit var manager: LocalPlaylistManager
    private lateinit var database: AppDatabase

    @get:Rule
    val trampolineScheduler = TrampolineSchedulerRule()

    @Before
    fun setup() {
        database = TestDatabase.createReplacingNewPipeDatabase()
        manager = LocalPlaylistManager(database)
    }

    @After
    fun cleanUp() {
        database.close()
    }

    @Test
    fun createPlaylist() {
        val NEWPIPE_URL = "https://newpipe.net/"
        val stream = StreamEntity(
            serviceId = 1,
            url = NEWPIPE_URL,
            title = "title",
            streamType = StreamType.VIDEO_STREAM,
            duration = 1,
            uploader = "uploader",
            uploaderUrl = NEWPIPE_URL
        )

        val result = manager.createPlaylist("name", listOf(stream))

        // This should not behave like this.
        // Currently list of all stream ids is returned instead of playlist id
        result.test().await().assertValue(listOf(1L))
    }

    @Test
    fun createPlaylist_emptyPlaylistMustReturnEmpty() {
        val result = manager.createPlaylist("name", emptyList())

        // This should not behave like this.
        // It should throw an error because currently the result is null
        result.test().await().assertComplete()
        manager.playlists.test().awaitCount(1).assertValue(emptyList())
    }

    @Test()
    fun createPlaylist_nonExistentStreamsAreUpserted() {
        val stream = StreamEntity(
            serviceId = 1,
            url = "https://newpipe.net/",
            title = "title",
            streamType = StreamType.VIDEO_STREAM,
            duration = 1,
            uploader = "uploader",
            uploaderUrl = "https://newpipe.net/"
        )
        database.streamDAO().insert(stream)
        val upserted = StreamEntity(
            serviceId = 1,
            url = "https://newpipe.net/2",
            title = "title2",
            streamType = StreamType.VIDEO_STREAM,
            duration = 1,
            uploader = "uploader",
            uploaderUrl = "https://newpipe.net/"
        )

        val result = manager.createPlaylist("name", listOf(stream, upserted))

        result.test().await().assertComplete()
        database.streamDAO().getAll().test().awaitCount(1).assertValue(listOf(stream, upserted))
    }

    @Test
    fun updateJoin_persistsReorderedItems() {
        val id = createPlaylistAndGetId(
            stream("https://newpipe.net/1"),
            stream("https://newpipe.net/2"),
            stream("https://newpipe.net/3")
        )
        val ids = streamIdsOf(id)

        manager.updateJoin(id, listOf(ids[2], ids[0], ids[1]))
            .test().await().assertComplete()

        assertEquals(listOf(ids[2], ids[0], ids[1]), streamIdsOf(id))
    }

    @Test
    fun updateJoin_reassignsThumbnailWhenReferencedStreamRemoved() {
        val id = createPlaylistAndGetId(
            stream("https://newpipe.net/1"),
            stream("https://newpipe.net/2"),
            stream("https://newpipe.net/3")
        )
        val ids = streamIdsOf(id)
        manager.changePlaylistThumbnail(id, ids[1], false).test().await().assertComplete()
        assertEquals(ids[1], thumbnailOf(id))

        // Remove the stream the thumbnail points to (ids[1]).
        manager.updateJoin(id, listOf(ids[0], ids[2])).test().await().assertComplete()

        // Thumbnail and order are updated together: it falls back to the new first stream.
        assertEquals(ids[0], thumbnailOf(id))
        assertEquals(listOf(ids[0], ids[2]), streamIdsOf(id))
    }

    @Test
    fun updateJoin_keepsThumbnailWhenReferencedStreamRemains() {
        val id = createPlaylistAndGetId(
            stream("https://newpipe.net/1"),
            stream("https://newpipe.net/2"),
            stream("https://newpipe.net/3")
        )
        val ids = streamIdsOf(id)
        manager.changePlaylistThumbnail(id, ids[1], false).test().await().assertComplete()

        // Reorder but keep ids[1] present: the thumbnail must not change.
        manager.updateJoin(id, listOf(ids[2], ids[1])).test().await().assertComplete()

        assertEquals(ids[1], thumbnailOf(id))
    }

    @Test
    fun updateJoin_keepsPermanentThumbnailEvenWhenStreamRemoved() {
        val id = createPlaylistAndGetId(
            stream("https://newpipe.net/1"),
            stream("https://newpipe.net/2"),
            stream("https://newpipe.net/3")
        )
        val ids = streamIdsOf(id)
        manager.changePlaylistThumbnail(id, ids[1], true).test().await().assertComplete()

        // Remove ids[1]; a user-pinned (permanent) thumbnail must stay untouched.
        manager.updateJoin(id, listOf(ids[0], ids[2])).test().await().assertComplete()

        assertEquals(ids[1], thumbnailOf(id))
    }

    @Test
    fun updateJoin_emptyListResetsThumbnailToDefault() {
        val id = createPlaylistAndGetId(
            stream("https://newpipe.net/1"),
            stream("https://newpipe.net/2")
        )
        val ids = streamIdsOf(id)
        manager.changePlaylistThumbnail(id, ids[0], false).test().await().assertComplete()

        manager.updateJoin(id, emptyList()).test().await().assertComplete()

        assertEquals(PlaylistEntity.DEFAULT_THUMBNAIL_ID, thumbnailOf(id))
    }

    private fun stream(url: String, title: String = "title") = StreamEntity(
        serviceId = 1,
        url = url,
        title = title,
        streamType = StreamType.VIDEO_STREAM,
        duration = 1,
        uploader = "uploader",
        uploaderUrl = "https://newpipe.net/"
    )

    private fun createPlaylistAndGetId(vararg streams: StreamEntity): Long {
        manager.createPlaylist("name", streams.toList()).test().await().assertComplete()
        return database.playlistDAO().getAll().blockingFirst()[0].uid
    }

    private fun streamIdsOf(playlistId: Long): List<Long> =
        manager.getPlaylistStreams(playlistId).blockingFirst().map { it.streamId }

    private fun thumbnailOf(playlistId: Long): Long =
        database.playlistDAO().getPlaylist(playlistId).blockingFirst()[0].thumbnailStreamId
}
