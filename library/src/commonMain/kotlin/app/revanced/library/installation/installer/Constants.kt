package app.revanced.library.installation.installer

@Suppress("MemberVisibilityCanBePrivate")
internal object Constants {
    const val PLACEHOLDER = "PLACEHOLDER"

    const val SELINUX_CONTEXT = "u:object_r:apk_data_file:s0"
    const val MOUNT_ROOT_PATH = "/data/adb/revanced"
    const val PACKAGE_MOUNT_PATH = "$MOUNT_ROOT_PATH/$PLACEHOLDER"
    const val MOUNTED_APK_PATH = "$PACKAGE_MOUNT_PATH/base.apk"
    const val MOUNT_SCRIPT_PATH = "/data/adb/service.d/mount_revanced_$PLACEHOLDER.sh"
    const val TMP_FILE_PATH = "/data/local/tmp/$PLACEHOLDER"

    const val EXISTS = "[[ -f $PLACEHOLDER ]] || exit 1"
    const val MOUNT_GREP = "grep -F $PLACEHOLDER /proc/mounts"
    const val DELETE = "rm -rf $PLACEHOLDER"
    const val CREATE_INSTALLATION_PATH = "mkdir -p $PLACEHOLDER"
    const val RESTART = "am start -S $PLACEHOLDER"
    const val KILL = "am force-stop $PLACEHOLDER"
    const val INSTALLED_APK_PATH = "pm path $PLACEHOLDER"
    const val GET_SDK_VERSION = "getprop ro.build.version.sdk"

    const val UMOUNT =
        "grep $PLACEHOLDER /proc/mounts | " +
                $$"while read -r line; do echo $line | " +
                "cut -d ' ' -f 2 | " +
                "xargs -r umount -l; done"

    val MOUNT_SCRIPT =
        $$"""
        #!/system/bin/sh
        until [ "$( getprop sys.boot_completed )" = 1 ]; do sleep 3; done
        until [ -d "/sdcard/Android" ]; do sleep 1; done

        mount_dir="$$PACKAGE_MOUNT_PATH"

        # Make sure the app is installed.
        if [ -z "$( pm path $$PLACEHOLDER )" ]; then
            exit 1
        fi

        # Unmount any existing installations to prevent multiple unnecessary mounts.
        $$UMOUNT

        # Mount using Magisk mirror, if available.
        if command -v magisk >/dev/null 2>&1; then
            if ! MAGISKTMP="$(magisk --path 2>/dev/null)"; then
                MAGISKTMP=/sbin
            fi
            MIRROR="$MAGISKTMP/.magisk/mirror"
            [ -d "$MIRROR" ] || MIRROR=""
        fi

        pm path $$PLACEHOLDER | sed 's/package://g' | while read -r stock_path; do
            [ -n "$stock_path" ] || continue

            apk_name="$(basename "$stock_path")"
            base_path="$mount_dir/$apk_name"

            [ -f "$base_path" ] || continue

            chcon $$SELINUX_CONTEXT "$base_path"
            mount -o bind "$MIRROR$base_path" "$stock_path"
        done

        # Kill the app to force it to restart the mounted APK in case it's currently running.
        $$KILL
        """.trimIndent()

    /**
     * Ensures a split name has the .apk extension.
     */
    fun splitFileName(splitName: String) =
        if (splitName.endsWith(".apk")) splitName else "$splitName.apk"

    /**
     * Replaces the [PLACEHOLDER] with the given [replacement].
     *
     * @param replacement The replacement to use.
     * @return The replaced string.
     */
    operator fun String.invoke(replacement: String) = replace(PLACEHOLDER, replacement)
}
