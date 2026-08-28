#!/usr/bin/env bash

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
mode="${1:-all}"

usage() {
  echo "Usage: $0 [all|android|ios]" >&2
}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command not found: $1" >&2
    exit 1
  fi
}

verify_android() {
  require_command java

  echo "==> Verifying Android"
  (
    cd "$repo_root"
    ./gradlew --no-daemon testDebugUnitTest assembleDebug lintDebug
  )
}

ios_destination() {
  if [ -n "${OPEN_GROOVE_IOS_DESTINATION:-}" ]; then
    printf '%s\n' "$OPEN_GROOVE_IOS_DESTINATION"
    return
  fi

  local device_id
  device_id="${OPEN_GROOVE_IOS_DEVICE_ID:-}"
  if [ -z "$device_id" ]; then
    device_id="$(xcrun simctl list devices available | awk -F '[()]' '/^[[:space:]]+iPhone/ && /\(Booted\)/ {gsub(/[[:space:]]/, "", $2); print $2; exit}')"
  fi
  if [ -z "$device_id" ]; then
    device_id="$(xcrun simctl list devices available | awk -F '[()]' '/^[[:space:]]+iPhone/ {gsub(/[[:space:]]/, "", $2); print $2; exit}')"
  fi

  if [ -z "$device_id" ]; then
    echo "No available iPhone simulator was found." >&2
    echo "Set OPEN_GROOVE_IOS_DESTINATION or OPEN_GROOVE_IOS_DEVICE_ID explicitly." >&2
    exit 1
  fi

  printf 'platform=iOS Simulator,id=%s\n' "$device_id"
}

verify_ios() {
  if [ "$(uname -s)" != "Darwin" ]; then
    echo "iOS verification requires macOS with full Xcode." >&2
    exit 1
  fi

  require_command java
  require_command xcodegen
  require_command xcodebuild
  require_command xcrun

  local project_file="$repo_root/iosApp/OpenGroove.xcodeproj/project.pbxproj"
  local project_hash_before
  local project_hash_after
  local destination
  local derived_data="${OPEN_GROOVE_DERIVED_DATA:-/tmp/OpenGrooveDerivedData}"

  project_hash_before="$(shasum -a 256 "$project_file" | awk '{print $1}')"
  (
    cd "$repo_root/iosApp"
    xcodegen generate
  )
  project_hash_after="$(shasum -a 256 "$project_file" | awk '{print $1}')"

  if [ "$project_hash_before" != "$project_hash_after" ]; then
    echo "The generated Xcode project was stale and has been refreshed." >&2
    echo "Review and commit iosApp/OpenGroove.xcodeproj before retrying." >&2
    exit 1
  fi

  destination="$(ios_destination)"
  echo "==> Verifying iOS on $destination"
  xcodebuild \
    -project "$repo_root/iosApp/OpenGroove.xcodeproj" \
    -scheme OpenGroove \
    -destination "$destination" \
    -derivedDataPath "$derived_data" \
    test
}

case "$mode" in
  all)
    verify_android
    verify_ios
    ;;
  android)
    verify_android
    ;;
  ios)
    verify_ios
    ;;
  *)
    usage
    exit 2
    ;;
esac

echo "==> OpenGroove $mode verification passed"
