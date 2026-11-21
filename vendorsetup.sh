# Automatically apply the SystemUI traceName visibility fix.
onyx_apply_systemui_trace_patch() {
    local top
    top="$(gettop)" || return 1

    local framework="$top/frameworks/base"
    local patch="$top/device/xiaomi/onyx/patches/0001-Fix-SystemUI-traceName-visibility.patch"
    local tile="$framework/packages/SystemUI/src/com/android/systemui/qs/panels/ui/compose/infinitegrid/Tile.kt"

    # Skip when the framework is unavailable.
    if [[ ! -d "$framework" ]]; then
        echo "[onyx] Framework not found, skipping SystemUI patch"
        return 0
    fi

    # Skip when the target file does not exist.
    if [[ ! -f "$tile" ]]; then
        echo "[onyx] SystemUI target not found, skipping patch"
        return 0
    fi

    # Check whether the fix is already present.
    if grep -qE '^[[:space:]]*internal[[:space:]]+val[[:space:]]+TileViewModel\.traceName' "$tile"; then
        echo "[onyx] SystemUI traceName fix already applied"
        return 0
    fi

    # Check whether the ROM requires this fix.
    if ! grep -qE '^[[:space:]]*private[[:space:]]+val[[:space:]]+TileViewModel\.traceName' "$tile"; then
        echo "[onyx] SystemUI traceName fix not required"
        return 0
    fi

    # Verify that the patch exists.
    if [[ ! -s "$patch" ]]; then
        echo "[onyx] WARNING: SystemUI patch file not found"
        return 0
    fi

    # Check whether the patch is already applied.
    if git -C "$framework" apply --reverse --check "$patch" 2>/dev/null; then
        echo "[onyx] SystemUI patch already applied"
        return 0
    fi

    # Apply the patch if compatible.
    if git -C "$framework" apply --check "$patch" 2>/dev/null; then
        if git -C "$framework" apply "$patch"; then
            echo "[onyx] SystemUI traceName patch applied successfully"
            return 0
        else
            echo "[onyx] ERROR: Failed to apply SystemUI patch"
            return 1
        fi
    fi

    # Do not interrupt environment setup on incompatible ROMs.
    echo "[onyx] WARNING: SystemUI patch incompatible with current framework"
    echo "[onyx] Skipping patch to avoid modifying incompatible sources"

    return 0
}

# Run automatically when vendorsetup.sh is sourced.
onyx_apply_systemui_trace_patch

# Clean up function after execution.
unset -f onyx_apply_systemui_trace_patch
