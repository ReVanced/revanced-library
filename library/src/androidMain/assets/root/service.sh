#!/system/bin/sh
DIR=${0%/*}

package_name="__PKG_NAME__"
version="__VERSION__"
label="__LABEL__"
sanitized_package_name=$(echo "$package_name" | sed 's/\./_/g')

ReadVolumeKeys() {
    local result=$(getevent -ql | while read dev type code value; do
        case "$code" in
            KEY_VOLUMEUP) [ "$value" = "DOWN" ] && echo 1 && break ;;
            KEY_VOLUMEDOWN) [ "$value" = "DOWN" ] && echo 2 && break ;;
        esac
    done)
    return "${result:-0}"
}

vibrate() {
    su -lp 2000 -c "cmd vibrator vibrate ${1:-500}" > /dev/null 2>&1
}

notify() {
    su -lp 2000 -c "cmd notification post -S bigtext -t '$1' 'ReVancedInduction' '$2'" > /dev/null 2>&1
}

rm -f "$DIR/log"

{
# Induction check for $package_name

# This loop waits for the system to finish booting before attempting the bind-mount.
# This is required for boot-time execution (service.sh) but is not needed for
# manual/direct mounts performed while the system is already running.
until [ "$(getprop sys.boot_completed)" = 1 ]; do sleep 5; done
# Wait a bit more for package manager to settle
sleep 10

base_path="$DIR/system/app/$sanitized_package_name/base.apk"
if [ ! -f "$base_path" ]; then
    # Fallback to old path for compatibility during transition
    base_path="$DIR/$package_name.apk"
fi

stock_path="$(pm path "$package_name" | grep base | sed 's/package://g' | head -n 1)"
stock_version="$(dumpsys package "$package_name" | grep versionName | cut -d "=" -f2 | head -n 1 | sed 's/ //g')"

echo "Base path: $base_path"
echo "Stock path: $stock_path"
echo "Base version: $version"
echo "Stock version: $stock_version"

if [ -z "$stock_path" ]; then
  echo "App $package_name is not installed. System app induction might have failed or still being processed."
  exit 1
fi

if echo "$stock_path" | grep -q "^/system/"; then
  echo "App is already running from system partition (likely our Magisk overlay). Proceeding with mount."
fi

if mount | grep -q "$stock_path" ; then
  echo "Stock path is already mounted. Performing remount."
  umount -l "$stock_path"
fi

if [ "$version" != "$stock_version" ]; then
  echo "The version of the installed app ($stock_version) does not match the patched app ($version)."
  
  vibrate 300
  notify "Version Mismatch for $label" "Press Volume Up to mount anyway, or Volume Down to skip."
  
  ReadVolumeKeys
  case $? in
    2)
      echo "User pressed Volume Down. Skipping bind mount."
      exit 0
      ;;
    *)
      echo "User pressed Volume Up. Proceeding with mount."
      ;;
  esac
fi

echo "Setting permissions for $base_path"
chmod 644 "$base_path"
chown system:system "$base_path"
if echo "$base_path" | grep -q "/system/"; then
  chcon u:object_r:system_file:s0 "$base_path"
else
  chcon u:object_r:apk_data_file:s0 "$base_path"
fi

echo "Mounting patched APK over stock path ($base_path => $stock_path)"
mount -o bind "$base_path" "$stock_path"

} >> "$DIR/log"
