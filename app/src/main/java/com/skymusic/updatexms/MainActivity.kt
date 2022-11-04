package com.skymusic.updatexms

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.blankj.utilcode.util.AppUtils
import com.blankj.utilcode.util.FileIOUtils
import com.blankj.utilcode.util.FileUtils
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.PathUtils
import com.blankj.utilcode.util.Utils
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

class MainActivity : AppCompatActivity(), CoroutineScope {
    private val XMS_PACKAGE = "com.nct.xmsbox"
    private val XMS_UPDATE_PACKAGE = "com.skymusic.updatexms"
    private var FOLDER_XMS = ""
    private var XMS_PATH_APK = ""
    private var checkInstall: Boolean? = false
    private var subscriber: Disposable? = null
    private var taskJob: Job? = null

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Utils.init(application)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            RPermission.instance.checkPermission(
                this,
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.INSTALL_PACKAGES,
                    Manifest.permission.REQUEST_INSTALL_PACKAGES
                )
            )
        } else {
            RPermission.instance.checkPermission(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.INSTALL_PACKAGES)
            )
        }
        FOLDER_XMS = PathUtils.getExternalAppMusicPath().replace(XMS_UPDATE_PACKAGE, XMS_PACKAGE) + File.separator
        val linkUpdate = FileIOUtils.readFile2String(FOLDER_XMS + "xMusic.txt") ?: ""
        XMS_PATH_APK = if (linkUpdate.isNotEmpty()) {
            linkUpdate
        } else {
            FOLDER_XMS + "Xms.apk"
        }
        initiateJob(1000) {
            Log.e("MainActivity:", "" + FileUtils.isFileExists(XMS_PATH_APK))
            if (FileUtils.isFileExists(XMS_PATH_APK)) {
                Log.e("isFileExists", "")
                checkInstall = DangerousUtils.installAppSilent(XMS_PATH_APK, "-r")
                subscriber = Observable.interval(10, 5, TimeUnit.SECONDS)
                    .observeOn(AndroidSchedulers.mainThread())
                    .take(15)
                    .subscribe({
                        LogUtils.e("isAppInstalled=" + AppUtils.isAppInstalled(XMS_PACKAGE) + "checkInstall=" + (checkInstall as Boolean))
                        if (AppUtils.isAppInstalled(XMS_PACKAGE) && checkInstall as Boolean) {
                            launchXMS()
                        }
                    }, {
                        it.printStackTrace()
                        launchXMS()
                    }, {
                        launchXMS()
                    })
            } else {
                FileUtils.listFilesInDirWithFilter(FOLDER_XMS, {
                    it.name.endsWith(".apk")
                }, true)?.apply {
                    map {
                        DangerousUtils.installAppSilent(it.absolutePath, "-r")
                    }
                }
                AppUtils.launchApp(XMS_PACKAGE)
                initiateJob(500) {
                    finish()
                }
            }
        }
    }

    private fun launchXMS() {
        subscriber?.dispose()
        LogUtils.e("launchApp")
        FileUtils.delete(XMS_PATH_APK)
        AppUtils.launchApp(XMS_PACKAGE)
        initiateJob(500) {
            finish()
        }
    }

    private fun View.delayOnLifecycle(durationMillis: Long, dispatcher: CoroutineDispatcher = Dispatchers.Main, block: () -> Unit): Job? =
        findViewTreeLifecycleOwner()?.let { lifecycleOwner ->
            lifecycleOwner.lifecycle.coroutineScope.launch(dispatcher) {
                delay(durationMillis)
                block()
            }
        }

    private fun initiateJob(durationMillis: Long, block: () -> Unit) {
        cancelJob()
        taskJob = launch {
            delay(durationMillis)
            block()
        }
    }

    private fun cancelJob() {
        taskJob?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelJob()
        subscriber?.dispose()
    }
}
