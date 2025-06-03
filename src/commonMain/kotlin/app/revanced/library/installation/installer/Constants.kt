package app.revanced.library.installation.installer

@Suppress("MemberVisibilityCanBePrivate")
internal object Constants {
    const val PLACEHOLDER = "PLACEHOLDER"

    const val TMP_FILE_PATH = "/data/local/tmp/revanced.tmp"
    const val MOUNT_PATH = "/data/adb/revanced/"
    const val MOUNTED_APK_PATH = "$MOUNT_PATH$PLACEHOLDER.apk"
    const val MOUNT_SCRIPT_PATH = "/data/adb/service.d/mount_revanced_$PLACEHOLDER.sh"

    const val EXISTS = "[[ -f $PLACEHOLDER ]] || exit 1"
    const val MOUNT_GREP = "grep $PLACEHOLDER /proc/mounts"
    const val DELETE = "rm -rf $PLACEHOLDER"
    const val CREATE_DIR = "mkdir -p"
    const val RESTART = "am start -S $PLACEHOLDER"
    const val KILL = "am force-stop $PLACEHOLDER"
    const val INSTALLED_APK_PATH = "pm path $PLACEHOLDER"
    const val CREATE_INSTALLATION_PATH = "$CREATE_DIR $MOUNT_PATH"

    const val MOUNT_APK =
        "base_path=\"$MOUNTED_APK_PATH\" && " +
            "mv $TMP_FILE_PATH \$base_path && " +
            "chmod 644 \$base_path && " +
            "chown system:system \$base_path && " +
            "chcon u:object_r:apk_data_file:s0  \$base_path"

    const val UMOUNT =
        "grep $PLACEHOLDER /proc/mounts | " +
            "while read -r line; do echo \$line | " +
            "cut -d ' ' -f 2 | " +
            "sed 's/apk.*/apk/' | " +
            "xargs -r umount -l; done"

    const val INSTALL_MOUNT_SCRIPT = "mv $TMP_FILE_PATH $MOUNT_SCRIPT_PATH && chmod +x $MOUNT_SCRIPT_PATH"

    val MOUNT_SCRIPT =
        """
        #!/system/bin/sh
        until [ "$( getprop sys.boot_completed )" = 1 ]; do sleep 3; done
        until [ -d "/sdcard/Android" ]; do sleep 1; done

        stock_path=$( pm path $PLACEHOLDER | grep base | sed 's/package://g' )

        # Make sure the app is installed.
        if [ -z "${'$'}stock_path" ]; then
            exit 1
        fi

        # Unmount any existing installations to prevent multiple unnecessary mounts.
        $UMOUNT

        base_path="$MOUNTED_APK_PATH"

        chcon u:object_r:apk_data_file:s0 ${'$'}base_path

        # Mount using Magisk mirror, if available.
        if command -v magisk &> /dev/null; then
            MAGISKTMP="${'$'}(magisk --path)" || MAGISKTMP=/sbin
            MIRROR="${'$'}MAGISKTMP/.magisk/mirror"
            if [ -z "$(ls -A "${'$'}MIRROR" 2>/dev/null)" ]; then
                MIRROR=""
            fi
        fi

        mount -o bind ${'$'}MIRROR${'$'}base_path ${'$'}stock_path

        # Kill the app to force it to restart the mounted APK in case it's currently running.
        $KILL
        """.trimIndent()

    /**
     * Replaces the [PLACEHOLDER] with the given [replacement].
     *
     * @param replacement The replacement to use.
     * @return The replaced string.
     */
    operator fun String.invoke(replacement: String) = replace(PLACEHOLDER, replacement)
}
