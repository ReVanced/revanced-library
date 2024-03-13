package app.revanced.library.installation.installer

@Suppress("MemberVisibilityCanBePrivate")
object Constants {
    const val PLACEHOLDER = "PLACEHOLDER"

    const val TMP_FILE_PATH = "/data/local/tmp/revanced.tmp"
    const val INSTALLATION_PATH = "/data/adb/revanced/"
    const val PATCHED_APK_PATH = "$INSTALLATION_PATH$PLACEHOLDER.apk"
    const val MOUNT_SCRIPT_PATH = "/data/adb/service.d/mount_revanced_$PLACEHOLDER.sh"

    const val DELETE = "rm -rf $PLACEHOLDER"
    const val CREATE_DIR = "mkdir -p"
    const val RESTART = "am start -S $PLACEHOLDER"
    const val KILL = "am force-stop $PLACEHOLDER"
    const val GET_INSTALLED_PATH = "pm path $PLACEHOLDER"
    const val CREATE_INSTALLATION_PATH = "$CREATE_DIR $INSTALLATION_PATH"

    const val INSTALL_PATCHED_APK =
        "base_path=\"$PATCHED_APK_PATH\" && " +
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

        base_path="$PATCHED_APK_PATH"

        chcon u:object_r:apk_data_file:s0 ${'$'}base_path

        # Use Magisk mirror, if possible.
        if command -v magisk &> /dev/null; then
            MIRROR="${'$'}(magisk --path)/.magisk/mirror"
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
