import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// Sony Camera Magisk Module Build Script
plugins {
    base
}

version = "1.0"
group = "com.sony.camera"

val moduleName = "sony_camera_magisk"
val moduleId = "sony_camera"
val moduleVersion = "1.0"
val moduleVersionCode = "1"
val moduleAuthor = "Sony Camera Port Team"
val moduleDescription = "Sony Camera for LineageOS"

// 定義目錄
val projectDir = project.projectDir
val staticDir = File(projectDir, "static")
val outDir = File(projectDir.parentFile, "out")
val buildDir = File(projectDir, "build")
val moduleDir = File(buildDir, "module")

tasks.register("prepareModule") {
    doLast {
        // 建立模組目錄結構
        moduleDir.mkdirs()
        File(moduleDir, "system").mkdirs()
        File(moduleDir, "META-INF/com/google/android").mkdirs()
        
        println("準備模組目錄結構...")
        
        // 複製 static/system 目錄內容到 module/system
        val staticSystemDir = File(staticDir, "system")
        if (staticSystemDir.exists()) {
            staticSystemDir.copyRecursively(File(moduleDir, "system"), overwrite = true)
            println("已複製靜態檔案")
        }
        
        // 複製 out 目錄內容到 module/system
        if (outDir.exists()) {
            // 複製 priv-app
            val outPrivApp = File(outDir, "priv-app")
            if (outPrivApp.exists()) {
                val targetPrivApp = File(moduleDir, "system/priv-app")
                targetPrivApp.mkdirs()
                outPrivApp.copyRecursively(targetPrivApp, overwrite = true)
                println("已複製 priv-app")
            }
            
            // 複製 lib
            val outLib = File(outDir, "lib")
            if (outLib.exists()) {
                val targetLib = File(moduleDir, "system/lib")
                targetLib.mkdirs()
                outLib.copyRecursively(targetLib, overwrite = true)
                println("已複製 lib")
            }
            
            // 複製 lib64
            val outLib64 = File(outDir, "lib64")
            if (outLib64.exists()) {
                val targetLib64 = File(moduleDir, "system/lib64")
                targetLib64.mkdirs()
                outLib64.copyRecursively(targetLib64, overwrite = true)
                println("已複製 lib64")
            }
        }
        
        // 設定 cacaoserver 的可執行權限
        val cacaoserver = File(moduleDir, "system/bin/cacaoserver")
        if (cacaoserver.exists()) {
            cacaoserver.setExecutable(true)
            println("已設定 cacaoserver 可執行權限")
        }
        
        // 建立 module.prop
        val moduleProp = File(moduleDir, "module.prop")
        moduleProp.writeText("""
            id=$moduleId
            name=Sony Camera
            version=$moduleVersion
            versionCode=$moduleVersionCode
            author=$moduleAuthor
            description=$moduleDescription
            updateJson=
        """.trimIndent())
        println("已建立 module.prop")
        
        // 建立 update-binary
        val updateBinary = File(moduleDir, "META-INF/com/google/android/update-binary")
        updateBinary.writeText("""
            #!/sbin/sh
            
            ZIPFILE=${'$'}3
            OUTFD=${'$'}2
            
            ui_print() {
                echo "ui_print ${'$'}1" > /proc/self/fd/${'$'}OUTFD
                echo "ui_print" > /proc/self/fd/${'$'}OUTFD
            }
            
            ui_print "*****************************"
            ui_print "    Sony Camera Module       "
            ui_print "*****************************"
            ui_print " "
            
            # 掛載 /system
            mount /system 2>/dev/null
            mount -o rw,remount /system 2>/dev/null
            
            # 解壓模組
            ui_print "正在安裝..."
            cd /tmp
            mkdir -p sony_camera
            cd sony_camera
            unzip -o "${'$'}ZIPFILE" 2>&1
            
            # 複製檔案
            cp -af system/* /system/ 2>&1
            
            ui_print " "
            ui_print "安裝完成！"
            ui_print " "
            
            # 卸載 /system
            umount /system 2>/dev/null
            
            exit 0
        """.trimIndent())
        updateBinary.setExecutable(true)
        println("已建立 update-binary")
        
        // 建立 updater-script
        val updaterScript = File(moduleDir, "META-INF/com/google/android/updater-script")
        updaterScript.writeText("#MAGISK")
        println("已建立 updater-script")
        
        // 建立 sepolicy.rule (SELinux 策略檔案 for Android 15+)
        val sepolicyRule = File(moduleDir, "sepolicy.rule")
        sepolicyRule.writeText("""
            # Sony Camera SELinux Policy
            # Allow priv_app (camera app) to access cacaoserver service
            
            # Allow finding the service in service_manager
            allow priv_app cacaoserver_service:service_manager find
            
            # Allow binder communication with cacaoserver
            allow priv_app cacaoserver_service:binder { call transfer }
            
            # Allow cacaoserver to communicate back to priv_app
            allow cacaoserver priv_app:binder { call transfer }
            
            # Additional permissions if needed
            # allow priv_app cameraserver:binder { call transfer }
        """.trimIndent())
        println("已建立 sepolicy.rule")
        
        // 建立 service.sh (Magisk 開機服務腳本)
        val serviceSh = File(moduleDir, "service.sh")
        serviceSh.writeText("""
            #!/system/bin/sh
            # Sony Camera Service Starter
            # This script runs after boot is completed
            
            MODDIR=${'$'}{0%/*}
            
            # Wait for system to be fully ready
            until [ "${'$'}(getprop sys.boot_completed)" = "1" ]; do
                sleep 1
            done
            
            # Wait additional time for system services
            sleep 5
            
            # Inject SELinux policy rules
            # This is needed for APatch/KernelSU which doesn't auto-load sepolicy.rule
            MAGISKPOLICY="/data/adb/ap/bin/magiskpolicy"
            if [ ! -f "${'$'}MAGISKPOLICY" ]; then
                MAGISKPOLICY="/data/adb/magisk/magiskpolicy"
            fi
            if [ ! -f "${'$'}MAGISKPOLICY" ]; then
                MAGISKPOLICY="magiskpolicy"
            fi
            
            if command -v "${'$'}MAGISKPOLICY" > /dev/null 2>&1; then
                log -t sony_camera "Injecting SELinux policies..."
                # Create policy file
                cat > /data/local/tmp/sony_camera_policy.txt << 'EOFPOLICY'
allow priv_app cacaoserver_service service_manager find
allow priv_app cacaoserver_service binder call
allow priv_app cacaoserver_service binder transfer
allow cacaoserver priv_app binder call
allow cacaoserver priv_app binder transfer
EOFPOLICY
                
                # Apply policy
                "${'$'}MAGISKPOLICY" --live --apply /data/local/tmp/sony_camera_policy.txt
                if [ ${'$'}? -eq 0 ]; then
                    log -t sony_camera "SELinux policies injected successfully"
                else
                    log -t sony_camera "Failed to inject SELinux policies"
                fi
                
                # Clean up
                rm -f /data/local/tmp/sony_camera_policy.txt
            else
                log -t sony_camera "Warning: magiskpolicy not found, SELinux policies not applied"
            fi
            
            # Start cacaoserver if not already running
            if ! pgrep -x cacaoserver > /dev/null 2>&1; then
                nohup /system/bin/cacaoserver > /dev/null 2>&1 &
                sleep 1
                if pgrep -x cacaoserver > /dev/null 2>&1; then
                    log -t sony_camera "cacaoserver started successfully"
                else
                    log -t sony_camera "Failed to start cacaoserver"
                fi
            else
                log -t sony_camera "cacaoserver already running"
            fi
        """.trimIndent())
        serviceSh.setExecutable(true)
        println("已建立 service.sh")
        
        // 建立 customize.sh (Magisk 安裝腳本)
        val customizeSh = File(moduleDir, "customize.sh")
        customizeSh.writeText("""
            #!/system/bin/sh
            
            SKIPUNZIP=0
            
            ui_print "*****************************"
            ui_print "    Sony Camera Module       "
            ui_print "*****************************"
            ui_print " "
            ui_print "正在安裝 Sony Camera..."
            
            # 設定目錄和檔案權限
            set_perm_recursive ${'$'}MODPATH/system/priv-app 0 0 0755 0644
            set_perm_recursive ${'$'}MODPATH/system/app 0 0 0755 0644
            set_perm_recursive ${'$'}MODPATH/system/lib 0 0 0755 0644
            set_perm_recursive ${'$'}MODPATH/system/lib64 0 0 0755 0644
            set_perm_recursive ${'$'}MODPATH/system/vendor/lib 0 0 0755 0644
            set_perm_recursive ${'$'}MODPATH/system/framework 0 0 0755 0644
            set_perm_recursive ${'$'}MODPATH/system/etc 0 0 0755 0644
            
            # 設定 cacaoserver 可執行權限
            set_perm ${'$'}MODPATH/system/bin/cacaoserver 0 2000 0755
            
            ui_print " "
            ui_print "安裝完成！"
            ui_print "請重新啟動裝置以啟用 Sony Camera"
            ui_print "cacaoserver 服務將在開機後自動啟動"
        """.trimIndent())
        customizeSh.setExecutable(true)
        println("已建立 customize.sh")
    }
}

tasks.register<Zip>("buildModule") {
    dependsOn("prepareModule")
    
    from(moduleDir)
    archiveFileName.set("${moduleName}_v${moduleVersion}.zip")
    destinationDirectory.set(buildDir)
    
    doLast {
        println("========================================")
        println("模組打包完成！")
        println("輸出位置: ${buildDir}/${moduleName}_v${moduleVersion}.zip")
        println("========================================")
    }
}

// 設定 base plugin 的 build task 依賴我們的 buildModule
tasks.named("build") {
    dependsOn("buildModule")
}

defaultTasks("build")
