package app.revanced.library.installation.installer

@Suppress("MemberVisibilityCanBePrivate")
object Constants {
    const val PLACEHOLDER = "PLACEHOLDER"

    const val SELINUX_CONTEXT = "u:object_r:apk_data_file:s0"
    const val TMP_FILE_PATH = "/data/local/tmp/revanced.tmp"
    const val MOUNT_PATH = "/data/adb/revanced/"
    const val MOUNTED_APK_PATH = "$MOUNT_PATH$PLACEHOLDER/base.apk"
    const val MOUNT_SCRIPT_PATH = "/data/adb/service.d/mount_revanced_$PLACEHOLDER.sh"

    const val EXISTS = "[[ -f $PLACEHOLDER ]] || exit 1"
    const val MOUNT_GREP = "grep -F $PLACEHOLDER /proc/mounts"
    const val DELETE = "rm -rf $PLACEHOLDER"
    const val CREATE_DIR = "mkdir -p"
    const val RESTART = "am start -S $PLACEHOLDER"
    const val KILL = "am force-stop $PLACEHOLDER"
    const val INSTALLED_APK_PATH = "pm path $PLACEHOLDER"
    const val CREATE_INSTALLATION_PATH = "$CREATE_DIR $MOUNT_PATH$PLACEHOLDER"
    const val GET_SDK_VERSION = "getprop ro.build.version.sdk"

    const val MAGISK_MODULES_PATH = "/data/adb/modules/"
    const val MAGISK_MODULE_ID = "revanced_$PLACEHOLDER"
    const val MAGISK_MODULE_PATH = "$MAGISK_MODULES_PATH$MAGISK_MODULE_ID"

    const val MOVE = "mv $TMP_FILE_PATH $PLACEHOLDER"
    const val SET_FILE_PERMISSIONS = "chmod 644 $PLACEHOLDER && chown system:system $PLACEHOLDER && chcon $SELINUX_CONTEXT $PLACEHOLDER"

    val MAGISK_MODULE_PROP =
        """
        id=revanced_$PLACEHOLDER
        name=ReVanced $PLACEHOLDER
        version=1.0
        versionCode=1
        author=ReVanced
        description=Patched by ReVanced
        """.trimIndent()

    const val MOUNT_APK =
        "base_path=\"$MOUNTED_APK_PATH\" && " +
                "mkdir -p \"${"$"}(dirname \"${"$"}{base_path}\")\" && " +
                "mv $TMP_FILE_PATH \"${"$"}{base_path}\" && " +
                "chmod 644 \"${"$"}{base_path}\" && " +
                "chown system:system \"${"$"}{base_path}\" && " +
                "chcon $SELINUX_CONTEXT \"${"$"}{base_path}\""

    val UMOUNT =
        """
        grep $PLACEHOLDER /proc/mounts | 
        while read -r line; do echo ${"$"}{line} | 
        cut -d ' ' -f 2 | 
        sed 's/apk.*/apk/' | 
        xargs -r umount -l; done
        """.trimIndent()

    const val INSTALL_MOUNT_SCRIPT =
        "mv $TMP_FILE_PATH $MOUNT_SCRIPT_PATH && chmod +x $MOUNT_SCRIPT_PATH"

    val MOUNT_SCRIPT =
        """
        #!/system/bin/sh
        until [ "${"$"}( getprop sys.boot_completed )" = 1 ]; do sleep 3; done
        until [ -d "/sdcard/Android" ]; do sleep 1; done

        stock_path=${"$"}( pm path $PLACEHOLDER | grep base | sed 's/package://g' )

        # Make sure the app is installed.
        if [ -z "${"$"}{stock_path}" ]; then
            exit 1
        fi

        # Unmount any existing installations to prevent multiple unnecessary mounts.
        $UMOUNT

        base_path="${"$"}{MOUNTED_APK_PATH}"

        chcon $SELINUX_CONTEXT ${"$"}{base_path}

        # Mount using Magisk mirror, if available.
        if command -v magisk >/dev/null 2>&1; then
            if ! MAGISKTMP="${"$"}(magisk --path 2>/dev/null)"; then
                MAGISKTMP=/sbin
            fi
            MIRROR="${"$"}{MAGISKTMP}/.magisk/mirror"
            [ -d "${"$"}{MIRROR}" ] || MIRROR=""
        fi

        mount -o bind ${"$"}{MIRROR}${"$"}{base_path} ${"$"}{stock_path}

        # Kill the app to force it to restart the mounted APK in case it's currently running.
        $KILL
        """.trimIndent()

    /**
     * Induction module property template.
     * Placeholders: __PKG_NAME__, __VERSION__, __LABEL__
     */
    val INDUCTION_MODULE_PROP =
        """
        id=__PKG_NAME__-ReVanced
        name=__LABEL__ ReVanced
        version=__VERSION__
        versionCode=0
        author=ReVanced
        description=Mounts the patched APK on top of the original one
        """.trimIndent()

    /**
     * Induction service script template.
     * Placeholders: __PKG_NAME__, __VERSION__, __LABEL__
     */
    val INDUCTION_SERVICE_SCRIPT =
        """
        #!/system/bin/sh
        DIR=${"$"}{0%/*}

        package_name="__PKG_NAME__"
        version="__VERSION__"
        label="__LABEL__"
        sanitized_package_name=${"$"}(echo "${"$"}{package_name}" | sed 's/\./_/g')

        ReadVolumeKeys() {
            local result=${"$"}(getevent -ql | while read dev type code value; do
                case "${"$"}{code}" in
                    KEY_VOLUMEUP) [ "${"$"}{value}" = "DOWN" ] && echo 1 && break ;;
                    KEY_VOLUMEDOWN) [ "${"$"}{value}" = "DOWN" ] && echo 2 && break ;;
                esac
            done)
            return "${"$"}{result:-0}"
        }

        vibrate() {
            su -lp 2000 -c "cmd vibrator vibrate ${"$"}{1:-500}" > /dev/null 2>&1
        }

        notify() {
            su -lp 2000 -c "cmd notification post -S bigtext -t '${"$"}{1}' 'ReVancedInduction' '${"$"}{2}'" > /dev/null 2>&1
        }

        rm -f "${"$"}{DIR}/log"

        {
        # Induction check for ${"$"}{package_name}

        # This loop waits for the system to finish booting before attempting the bind-mount.
        # This is required for boot-time execution (service.sh) but is not needed for
        # manual/direct mounts performed while the system is already running.
        until [ "${"$"}(getprop sys.boot_completed)" = 1 ]; do sleep 5; done
        # Wait a bit more for package manager to settle
        sleep 10

        # Unified path for the patched APK (Source of truth)
        base_path="/data/adb/revanced/${"$"}{package_name}/base.apk"
        
        # Fallback to local path if unified path doesn't exist (Legacy compatibility)
        if [ ! -f "${"$"}{base_path}" ]; then
            base_path="${"$"}{DIR}/system/app/${"$"}{sanitized_package_name}/base.apk"
        fi
        if [ ! -f "${"$"}{base_path}" ]; then
            base_path="${"$"}{DIR}/${"$"}{package_name}.apk"
        fi

        stock_path="${"$"}(pm path "${"$"}{package_name}" | grep base | sed 's/package://g' | head -n 1)"
        stock_version="${"$"}(dumpsys package "${"$"}{package_name}" | grep versionName | cut -d "=" -f2 | head -n 1 | sed 's/ //g')"

        echo "Base path: ${"$"}{base_path}"
        echo "Stock path: ${"$"}{stock_path}"
        echo "Base version: ${"$"}{version}"
        echo "Stock version: ${"$"}{stock_version}"

        if [ -z "${"$"}{stock_path}" ]; then
          echo "App ${"$"}{package_name} is not installed. System app induction might have failed or still being processed."
          exit 1
        fi

        if echo "${"$"}{stock_path}" | grep -q "^/system/"; then
          echo "App is already running from system partition (likely our Magisk overlay). Proceeding with mount."
        fi

        if mount | grep -q "${"$"}{stock_path}" ; then
          echo "Stock path is already mounted. Performing remount."
          umount -l "${"$"}{stock_path}"
        fi

        if [ "${"$"}{version}" != "${"$"}{stock_version}" ]; then
          echo "The version of the installed app (${"$"}{stock_version}) does not match the patched app (${"$"}{version})."
          
          vibrate 300
          notify "Version Mismatch for ${"$"}{label}" "Press Volume Up to mount anyway, or Volume Down to skip."
          
          ReadVolumeKeys
          case ${"$"}{?} in
            2)
              echo "User pressed Volume Down. Skipping bind mount."
              exit 0
              ;;
            *)
              echo "User pressed Volume Up. Proceeding with mount."
              ;;
          esac
        fi

        echo "Setting permissions for ${"$"}{base_path}"
        chmod 644 "${"$"}{base_path}"
        chown system:system "${"$"}{base_path}"
        if echo "${"$"}{base_path}" | grep -q "/system/"; then
          chcon u:object_r:system_file:s0 "${"$"}{base_path}"
        else
          chcon u:object_r:apk_data_file:s0 "${"$"}{base_path}"
        fi

        echo "Mounting patched APK over stock path (${"$"}{base_path} => ${"$"}{stock_path})"
        mount -o bind "${"$"}{base_path}" "${"$"}{stock_path}"

        } >> "${"$"}{DIR}/log"
        """.trimIndent()

    /**
     * Replaces the [PLACEHOLDER] with the given [replacement].
     *
     * @param replacement The replacement to use.
     * @return The replaced string.
     */
    operator fun String.invoke(replacement: String) = replace(PLACEHOLDER, replacement)
}
