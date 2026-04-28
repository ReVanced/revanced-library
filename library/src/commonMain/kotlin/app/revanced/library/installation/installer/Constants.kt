package app.revanced.library.installation.installer

@Suppress("MemberVisibilityCanBePrivate")
object Constants {
    const val PLACEHOLDER = "PLACEHOLDER"

    const val SELINUX_CONTEXT = "u:object_r:apk_data_file:s0"
    const val TMP_FILE_PATH = "/data/local/tmp/revanced.tmp"
    const val MOUNT_PATH = "/data/adb/revanced/"
    const val MOUNTED_APK_PATH = "$MOUNT_PATH$PLACEHOLDER.apk"
    const val MOUNT_SCRIPT_PATH = "/data/adb/service.d/mount_revanced_$PLACEHOLDER.sh"
    const val HANDLE_DISABLED_SCRIPT_PATH = "/data/adb/service.d/revanced_handle_disabled_$PLACEHOLDER.sh"
    const val MODULE_PATH = "/data/adb/modules/revanced_$PLACEHOLDER"

    const val EXISTS = "[[ -f $PLACEHOLDER ]] || exit 1"
    const val MOUNT_GREP = "grep -F $PLACEHOLDER /proc/mounts"
    const val DELETE = "rm -rf $PLACEHOLDER"
    const val CREATE_DIR = "mkdir -p"
    const val RESTART = "am start -S $PLACEHOLDER"
    const val KILL = "am force-stop $PLACEHOLDER"
    const val INSTALLED_APK_PATH = "pm path $PLACEHOLDER"
    const val CREATE_INSTALLATION_PATH = "$CREATE_DIR $MOUNT_PATH$PLACEHOLDER"
    const val GET_SDK_VERSION = "getprop ro.build.version.sdk"
    const val MODULE_PROP_FILE = "module.prop"
    const val SERVICE_SCRIPT_FILE = "service.sh"
    const val UNINSTALL_SCRIPT_FILE = "uninstall.sh"
    const val PREPARE_APK =
        "base_path=\"$MOUNTED_APK_PATH\" && " +
                "mv $TMP_FILE_PATH \"\${base_path}\" && " +
                "chmod 644 \"\${base_path}\" && " +
                "chown system:system \"\${base_path}\" && " +
                "chcon $SELINUX_CONTEXT \"\${base_path}\""

    const val PREPARE_MOUNT_SCRIPT = "mv $TMP_FILE_PATH $MOUNT_SCRIPT_PATH && chmod +x $MOUNT_SCRIPT_PATH"

    /**
     * Magisk module property template.
     * The id MUST match the module directory name (revanced___PKG_NAME__) so that
     * Magisk can find the module by ID for enable/disable operations.
     *
     * Placeholders: __PKG_NAME__ (original package name)
     */
    val MODULE_PROP = """
        id=revanced___PKG_NAME__
        name=__PKG_NAME__ ReVanced
        version=1.0
        versionCode=0
        author=ReVanced
        description=Mounts the patched APK on top of the original one
        """.trimIndent()

    /**
     * Magisk module uninstall script template. Magisk runs this when the module is
     * removed via the Magisk app. It cleans up the patched APK file and the
     * boot-time handle-disabled script.
     *
     * Placeholders: __PKG_NAME__ (unpatched), __PATCHED_PKG__ (patched)
     */
    val MODULE_UNINSTALL_SCRIPT = """
        #!/system/bin/sh
        pm uninstall "__PATCHED_PKG__"
        rm -f "/data/adb/revanced/__PKG_NAME__.apk"
        rm -f "/data/adb/service.d/revanced_handle_disabled___PKG_NAME__.sh"
        """.trimIndent()

    /**
     * Boot-time handle-disabled script. Runs on every boot (via service.d, independent of module state).
     * Uninstalls the patched app when the module is disabled or removed, so the app
     * disappears when the module is toggled off.
     *
     * Placeholders: __PATCHED_PKG__ (patched), __PKG_NAME__ (unpatched)
     */
    val HANDLE_DISABLED_SCRIPT = $$"""
        #!/system/bin/sh
        patched_pkg="__PATCHED_PKG__"
        module_path="/data/adb/modules/revanced___PKG_NAME__"

        until [ "$(getprop sys.boot_completed)" = 1 ]; do sleep 5; done
        sleep 11

        # Module was fully removed. Uninstall app and self-destruct this script.
        if [ ! -d "${module_path}" ]; then
            pm uninstall "${patched_pkg}" 2>/dev/null
            rm -f "$0"
            exit 0
        fi

        # If service.sh did not run this boot, the module is disabled - disable the app so it
        # disappears from the launcher without losing data. service.sh re-enables it on next boot.
        current_boot_id=$(cat /proc/sys/kernel/random/boot_id 2>/dev/null)
        stored_boot_id=$(cat "${module_path}/.last_boot_id" 2>/dev/null)
        if [ "${stored_boot_id}" != "${current_boot_id}" ]; then
            pm disable-user --user __USER_ID__ "${patched_pkg}" 2>/dev/null
        fi
        """.trimIndent()

    val UMOUNT = $$"""
        grep -F "/$$PLACEHOLDER/" /proc/mounts |
        while read -r line; do echo ${line} |
        cut -d ' ' -f 2 |
        sed 's/apk.*/apk/' |
        xargs -r umount -l; done
        """.trimIndent()

    val MOUNT_SCRIPT = $$"""
        #!/system/bin/sh
        until [ "$(getprop sys.boot_completed)" = 1 ]; do sleep 3; done
        until [ -d "/sdcard/Android" ]; do sleep 1; done

        stock_path=$(pm path $$PLACEHOLDER | grep base | sed 's/package://g')

        # Make sure the app is installed.
        if [ -z "${stock_path}" ]; then
            exit 1
        fi

        # Unmount any existing installations to prevent multiple unnecessary mounts.
        $$UMOUNT

        base_path="$$MOUNTED_APK_PATH"

        chcon $$SELINUX_CONTEXT ${base_path}

        # Mount using Magisk mirror, if available.
        if command -v magisk >/dev/null 2>&1; then
            if ! MAGISKTMP="$(magisk --path 2>/dev/null)"; then
                MAGISKTMP=/sbin
            fi
            MIRROR="${MAGISKTMP}/.magisk/mirror"
            [ -d "${MIRROR}" ] || MIRROR=""
        fi

        mount -o bind ${MIRROR}${base_path} ${stock_path}

        # Kill the app to force it to restart the mounted APK in case it's currently running.
        $$KILL
        """.trimIndent()


    /**
     * Magisk module service script template. Runs on every boot when the module is enabled.
     * Installs the patched APK as a standalone app if not already installed.
     *
     * Placeholders: __PKG_NAME__ (original, used for APK path), __PATCHED_PKG__ (patched, used for pm commands)
     */
    val MODULE_SERVICE_SCRIPT = $$"""
        #!/system/bin/sh
        DIR=${0%/*}

        package_name="__PATCHED_PKG__"

        # Write a boot token so the handle-disabled script can detect whether the module was enabled this boot.
        cp /proc/sys/kernel/random/boot_id "${DIR}/.last_boot_id"

        until [ "$(getprop sys.boot_completed)" = 1 ]; do sleep 5; done

        # Wait until PM is fully responsive - sys.boot_completed=1 fires before the PM
        # binder handles transactions. Poll until it returns at least one package entry.
        until pm list packages --user __USER_ID__ 2>/dev/null | grep -q "^package:"; do sleep 5; done

        base_path="/data/adb/revanced/__PKG_NAME__.apk"

        [ ! -f "${base_path}" ] && exit 1

        # Re-enable the app if it was disabled by the handle-disabled script (module was toggled off
        # then back on). If not installed at all, fall through to the install block.
        if pm list packages --user __USER_ID__ | grep -q "^package:${package_name}$"; then
            pm enable --user __USER_ID__ "${package_name}" 2>/dev/null
        else
            # Retry loop - sys.boot_completed=1 fires before the PM binder is stable for
            # write transactions, causing "Failed transaction" errors. Pipe-based install
            # (pm install -S size < file) uses a simpler code path than session-based
            # (install-create/write/commit) and is less prone to early-boot binder failures.
            # NOTE: On Xiaomi devices (MIUI/HyperOS), pm install may still fail due to
            # package verification restrictions. Manual installation may be required.
            max_retries=3
            attempt=0
            install_exit=1

            while [ ${attempt} -lt ${max_retries} ] && [ ${install_exit} -ne 0 ]; do
                attempt=$((attempt + 1))
                pm install -r -d --user __USER_ID__ -S $(stat -c%s "${base_path}") < "${base_path}"
                install_exit=$?
                [ ${install_exit} -ne 0 ] && [ ${attempt} -lt ${max_retries} ] && sleep 5
            done
        fi
        """.trimIndent()

    /**
     * Replaces the [PLACEHOLDER] with the given [replacement].
     *
     * @param replacement The replacement to use.
     * @return The replaced string.
     */
    operator fun String.invoke(replacement: String) = replace(PLACEHOLDER, replacement)
}
