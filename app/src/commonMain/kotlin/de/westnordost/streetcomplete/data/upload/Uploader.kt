package de.westnordost.streetcomplete.data.upload

import de.westnordost.streetcomplete.ApplicationConstants
import de.westnordost.streetcomplete.data.AuthorizationException
import de.westnordost.streetcomplete.data.download.tiles.DownloadedTilesController
import de.westnordost.streetcomplete.data.download.tiles.enclosingTilePos
import de.westnordost.streetcomplete.data.osm.edits.upload.ElementEditsUploader
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.osmnotes.edits.NoteEditsUploader
import de.westnordost.streetcomplete.data.user.UserLoginController
import de.westnordost.streetcomplete.data.user.UserLoginSource
import de.westnordost.streetcomplete.util.Listeners
import de.westnordost.streetcomplete.util.logs.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Collects and uploads all user changes: notes created, comments left on existing
 * notes, quests answered, edits made in overlays, ...  */
class Uploader(
    private val noteEditsUploader: NoteEditsUploader,
    private val elementEditsUploader: ElementEditsUploader,
    private val downloadedTilesController: DownloadedTilesController,
    private val userLoginSource: UserLoginSource,
    private val versionIsBannedChecker: VersionIsBannedChecker,
    private val userLoginController: UserLoginController,
    private val mutex: Mutex
) : UploadProgressSource {

    private val listeners = Listeners<UploadProgressSource.Listener>()

    private lateinit var bannedInfo: BannedInfo

    private val uploadedChangeRelay = object : OnUploadedChangeListener {
        override fun onUploaded(editType: String, at: LatLon) {
            listeners.forEach { it.onUploaded(editType, at) }
        }

        override fun onDiscarded(editType: String, at: LatLon) {
            invalidateArea(at)
            listeners.forEach { it.onDiscarded(editType, at) }
        }
    }

    init {
        noteEditsUploader.uploadedChangeListener = uploadedChangeRelay
        elementEditsUploader.uploadedChangeListener = uploadedChangeRelay
    }

    override var isUploadInProgress: Boolean = false
        private set

    suspend fun upload() {
        try {
            isUploadInProgress = true
            listeners.forEach { it.onStarted() }

            if (!::bannedInfo.isInitialized) {
                bannedInfo = versionIsBannedChecker.get()
            }
            val banned = bannedInfo
            if (banned is BannedInfo.IsBanned) {
                throw VersionBannedException(banned.reason)
            }

            // let's fail early in case of no authorization
            if (!userLoginSource.isLoggedIn) {
                throw AuthorizationException("User is not authorized")
            }

            Log.i(TAG, "Starting upload")

            mutex.withLock {
                // element edit and note edit uploader must run in sequence because the notes may need
                // to be updated if the element edit uploader creates new elements to which notes refer
                elementEditsUploader.upload()
                noteEditsUploader.upload()
            }
            Log.i(TAG, "Finished upload")
        } catch (e: CancellationException) {
            Log.i(TAG, "Upload cancelled")
        } catch (e: Exception) {
            Log.e(TAG, "Unable to upload", e)
            if (e is AuthorizationException) {
                userLoginController.logOut()
            }
            listeners.forEach { it.onError(e) }
            throw e
        } finally {
            isUploadInProgress = false
            listeners.forEach { it.onFinished() }
        }
    }

    override fun addListener(listener: UploadProgressSource.Listener) {
        listeners.add(listener)
    }
    override fun removeListener(listener: UploadProgressSource.Listener) {
        listeners.remove(listener)
    }

    private fun invalidateArea(pos: LatLon) {
        // called after a conflict. If there is a conflict, the user is not the only one in that
        // area, so best invalidate all downloaded quests here and re-download on next occasion
        val tile = pos.enclosingTilePos(ApplicationConstants.DOWNLOAD_TILE_ZOOM)
        downloadedTilesController.invalidate(tile)
    }

    companion object {
        const val TAG = "Upload"
    }
}
