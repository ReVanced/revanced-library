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

    const val MAGISK_MODULES_PATH = "/data/adb/modules/"
    const val MAGISK_MODULE_ID = "revanced_$PLACEHOLDER"
    const val MAGISK_MODULE_PATH = "$MAGISK_MODULES_PATH$MAGISK_MODULE_ID"
    const val STAGE_APK =
        "base_path=\"$MOUNTED_APK_PATH\" && " +
                "mkdir -p \"\$(dirname \"\${base_path}\")\" && " +
                "mv $TMP_FILE_PATH \"\${base_path}\" && " +
                "chmod 644 \"\${base_path}\" && " +
                "chown system:system \"\${base_path}\" && " +
                "chcon $SELINUX_CONTEXT \"\${base_path}\""

    const val INSTALL_MOUNT_SCRIPT = "mv $TMP_FILE_PATH $MOUNT_SCRIPT_PATH && chmod +x $MOUNT_SCRIPT_PATH"

    const val MOVE = "mv $TMP_FILE_PATH $PLACEHOLDER"
    const val SET_MOUNTING_PERMISSIONS = "chmod 644 $PLACEHOLDER && chown system:system $PLACEHOLDER && chcon $SELINUX_CONTEXT $PLACEHOLDER"

    /**
     * Magisk module property template.
     * The id MUST match the module directory name (revanced___FORMATTED_PKG__) so that
     * Magisk can find the module by ID for enable/disable operations.
     *
     * Placeholders: __FORMATTED_PKG__ (original with dots→underscores), __VERSION__, __LABEL__
     */
    val MAGISK_MODULE_PROP = """
        id=revanced___FORMATTED_PKG__
        name=__PKG_NAME__ ReVanced
        version=1.0
        versionCode=0
        author=ReVanced
        description=Mounts the patched APK on top of the original one
        """.trimIndent()

    /**
     * Magisk module uninstall script template. Magisk runs this when the module is
     * removed via the Magisk app. It cleans up the unified source-of-truth APK and
     * the boot-time handle-disabled script.
     *
     * Placeholders: __PKG_NAME__ (original), __PATCHED_PKG__ (patched), __FORMATTED_PKG__ (original with dots→underscores)
     */
    val MAGISK_UNINSTALL_SCRIPT = """
        #!/system/bin/sh
        pm uninstall --user 0 "__PATCHED_PKG__"
        rm -f "/data/adb/revanced/__PKG_NAME__.apk"
        rm -f "/data/adb/service.d/revanced_handle_disabled___FORMATTED_PKG__.sh"
        """.trimIndent()

    /**
     * Boot-time handle-disabled script. Runs on every boot (via service.d, independent of module state).
     * Uninstalls the patched app when the module is disabled or removed, so the app
     * disappears when the module is toggled off.
     *
     * Placeholders: __PATCHED_PKG__ (patched), __FORMATTED_PKG__ (original with dots→underscores)
     */
    val HANDLE_DISABLED_SCRIPT = $$"""
        #!/system/bin/sh
        patched_pkg="__PATCHED_PKG__"
        module_path="/data/adb/modules/revanced___FORMATTED_PKG__"

        until [ "$(getprop sys.boot_completed)" = 1 ]; do sleep 5; done
        sleep 11

        # Module was fully removed — uninstall app and self-destruct this script.
        if [ ! -d "${module_path}" ]; then
            pm uninstall --user 0 "${patched_pkg}" 2>/dev/null
            rm -f "$0"
            exit 0
        fi

        # If service.sh did not run this boot, the module is disabled — disable the app so it
        # disappears from the launcher without losing data. service.sh re-enables it on next boot.
        current_boot_id=$(cat /proc/sys/kernel/random/boot_id 2>/dev/null)
        stored_boot_id=$(cat "${module_path}/.boot_token" 2>/dev/null)
        if [ "${stored_boot_id}" != "${current_boot_id}" ]; then
            pm disable-user --user 0 "${patched_pkg}" 2>/dev/null
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

        # Write a boot token so the handle-disabled script can detect whether service.sh ran this boot.
        cp /proc/sys/kernel/random/boot_id "${DIR}/.boot_token"

        LOG="${DIR}/log"
        MAX_LOG_LINES=200

        # Trim log to last MAX_LOG_LINES lines to prevent unbounded growth.
        if [ -f "${LOG}" ]; then
            tail -n "${MAX_LOG_LINES}" "${LOG}" > "${LOG}.tmp" && mv "${LOG}.tmp" "${LOG}"
        fi

        {

        echo "--- $(date '+%Y-%m-%d %H:%M:%S') | pkg=${package_name} ---"

        until [ "$(getprop sys.boot_completed)" = 1 ]; do sleep 5; done

        # Wait until PM is fully responsive — sys.boot_completed=1 fires before the PM
        # binder handles transactions. Poll until it returns at least one package entry.
        until pm list packages --user 0 2>/dev/null | grep -q "^package:"; do sleep 5; done

        base_path="/data/adb/revanced/__PKG_NAME__.apk"

        echo "Base path: ${base_path}"

        if [ ! -f "${base_path}" ]; then
            echo "Patched APK not found."
            exit 1
        fi

        # Re-enable the app if it was disabled by the handle-disabled script (module was toggled off
        # then back on). If not installed at all, fall through to the install block.
        if pm list packages --user 0 | grep -q "^package:${package_name}$"; then
            pm enable --user 0 "${package_name}" 2>/dev/null
            echo "Package enabled."
        else
            # Retry loop — sys.boot_completed=1 fires before the PM binder is stable for
            # write transactions, causing "Failed transaction" errors. Pipe-based install
            # (pm install -S size < file) uses a simpler code path than session-based
            # (install-create/write/commit) and is less prone to early-boot binder failures.
            # NOTE: On Xiaomi devices (MIUI/HyperOS), pm install may still fail due to
            # package verification restrictions. Manual install via ReVanced Manager
            # may be required in that case.
            max_retries=3
            attempt=0
            install_exit=1

            while [ ${attempt} -lt ${max_retries} ] && [ ${install_exit} -ne 0 ]; do
                attempt=$((attempt + 1))
                echo "Install attempt ${attempt}/${max_retries}..."
                pm install -r -d --user 0 -S $(stat -c%s "${base_path}") < "${base_path}"
                install_exit=$?
                echo "Install exit code: ${install_exit}"
                if [ ${install_exit} -ne 0 ] && [ ${attempt} -lt ${max_retries} ]; then
                    echo "Retrying in 15s..."
                    sleep 5
                fi
            done
        fi

        } >> "${LOG}"
        """.trimIndent()

    /**
     * Replaces the [PLACEHOLDER] with the given [replacement].
     *
     * @param replacement The replacement to use.
     * @return The replaced string.
     */
    operator fun String.invoke(replacement: String) = replace(PLACEHOLDER, replacement)
}
