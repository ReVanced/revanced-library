package app.revanced.library.installation.installer

object Constants {
    const val PLACEHOLDER = "PLACEHOLDER"

    const val TMP_PATH = "/data/local/tmp/revanced.tmp"
    const val INSTALLATION_PATH = "/data/adb/revanced/"
    const val PATCHED_APK_PATH = "$INSTALLATION_PATH$PLACEHOLDER.apk"
    const val MOUNT_SCRIPT_PATH = "/data/adb/service.d/mount_revanced_$PLACEHOLDER.sh"

    const val DELETE = "rm -rf $PLACEHOLDER"
    const val CREATE_DIR = "mkdir -p"
    const val RESTART = "am start -S $PLACEHOLDER"
    const val KILL = "am force-stop $PLACEHOLDER"
    const val GET_INSTALLED_PATH = "pm path $PLACEHOLDER"

    const val INSTALL_PATCHED_APK =
        "base_path=\"$PATCHED_APK_PATH\" && " +
            "mv $TMP_PATH ${'$'}base_path && " +
            "chmod 644 ${'$'}base_path && " +
            "chown system:system ${'$'}base_path && " +
            "chcon u:object_r:apk_data_file:s0  ${'$'}base_path"

    const val UMOUNT =
        "grep $PLACEHOLDER /proc/mounts | while read -r line; do echo ${'$'}line | cut -d ' ' -f 2 | sed 's/apk.*/apk/' | xargs -r umount -l; done"

    const val INSTALL_MOUNT_SCRIPT = "mv $TMP_PATH $MOUNT_SCRIPT_PATH && chmod +x $MOUNT_SCRIPT_PATH"

    val MOUNT_SCRIPT =
        """
        #!/system/bin/sh
        MAGISKTMP="$( magisk --path )" || MAGISKTMP=/sbin
        MIRROR="${'$'}MAGISKTMP/.magisk/mirror"

        until [ "$( getprop sys.boot_completed )" = 1 ]; do sleep 3; done
        until [ -d "/sdcard/Android" ]; do sleep 1; done

        # Unmount any existing mount as a safety measure
        $UMOUNT

        base_path="$PATCHED_APK_PATH"
        stock_path=$( pm path $PLACEHOLDER | grep base | sed 's/package://g' )
        
        # Check if the app is installed
        if [ -z "${'$'}stock_path" ]; then
            exit 1
        fi

        chcon u:object_r:apk_data_file:s0 ${'$'}base_path
        mount -o bind ${'$'}MIRROR${'$'}base_path ${'$'}stock_path

        # Kill the app to force it to restart the mounted APK in case it's already running
        $KILL
        """.trimIndent()
}
