#!/usr/bin/env bash
# Read-only capture. Does not change system settings or include notification bodies.
set -euo pipefail
if [[ $# -ne 1 ]]; then
    echo "Usage: $0 OUTPUT_DIRECTORY" >&2
    exit 2
fi
aod_adb_bin="${AOD_ADB_BIN:-adb}"
aod_output_dir="$1"
mkdir -p "$aod_output_dir"
"$aod_adb_bin" get-state >/dev/null
"$aod_adb_bin" shell getprop ro.product.model > "$aod_output_dir/model.txt"
"$aod_adb_bin" shell getprop ro.build.display.id > "$aod_output_dir/firmware.txt"
"$aod_adb_bin" shell dumpsys activity service dev.lutergs.sgaod/.service.AODService > "$aod_output_dir/aod.txt"
"$aod_adb_bin" shell dumpsys display > "$aod_output_dir/display.txt"
"$aod_adb_bin" shell dumpsys power > "$aod_output_dir/power.txt"
"$aod_adb_bin" shell dumpsys sensorservice > "$aod_output_dir/sensors.txt"
echo "Saved diagnostics to $aod_output_dir"
