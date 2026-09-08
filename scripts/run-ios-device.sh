#!/usr/bin/env bash

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
configuration="${OPEN_GROOVE_IOS_CONFIGURATION:-Debug}"
derived_data="${OPEN_GROOVE_IOS_DERIVED_DATA:-/tmp/OpenGroovePhysicalDerivedData}"
development_team="${OPEN_GROOVE_DEVELOPMENT_TEAM:-}"
device_id="${OPEN_GROOVE_IOS_DEVICE_ID:-}"
build_only="${OPEN_GROOVE_IOS_BUILD_ONLY:-0}"
bundle_id="com.trulyfreemusic.opengroove.ios"

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    printf 'Required command not found: %s\n' "$1" >&2
    exit 1
  fi
}

require_command java
require_command xcodebuild
require_command xcodegen
require_command xcrun

if [ -z "$development_team" ]; then
  printf '%s\n' \
    'Set OPEN_GROOVE_DEVELOPMENT_TEAM to your Apple development team identifier.' \
    'Keep this machine-local value out of project.yml and source control.' >&2
  exit 1
fi

if [ -z "$device_id" ]; then
  device_id="$({ xcrun devicectl list devices || true; } | awk '
    /connected/ && /iPhone/ {
      for (field = 1; field <= NF; field += 1) {
        if (length($field) == 36 && $field ~ /-/) {
          print $field
          exit
        }
      }
    }
  ')"
fi

if [ -z "$device_id" ]; then
  printf '%s\n' \
    'No connected Apple device was found.' \
    'Unlock and trust the iPhone, enable Developer Mode, or set OPEN_GROOVE_IOS_DEVICE_ID.' >&2
  exit 1
fi

(
  cd "$repo_root/iosApp"
  xcodegen generate
)

printf '==> Building OpenGroove for the connected iPhone (%s)\n' "$configuration"
xcodebuild \
  -project "$repo_root/iosApp/OpenGroove.xcodeproj" \
  -scheme OpenGroove \
  -configuration "$configuration" \
  -destination "platform=iOS,id=$device_id" \
  -derivedDataPath "$derived_data" \
  -allowProvisioningUpdates \
  CODE_SIGN_STYLE=Automatic \
  DEVELOPMENT_TEAM="$development_team" \
  build

app_path="$derived_data/Build/Products/$configuration-iphoneos/OpenGroove.app"
if [ ! -d "$app_path" ]; then
  printf 'Built application was not found at %s\n' "$app_path" >&2
  exit 1
fi

if [ "$build_only" = "1" ]; then
  printf '==> Signed device build passed: %s\n' "$app_path"
  exit 0
fi

printf '%s\n' '==> Installing OpenGroove on the connected iPhone'
xcrun devicectl device install app --device "$device_id" "$app_path"

printf '%s\n' '==> Launching OpenGroove'
xcrun devicectl device process launch \
  --device "$device_id" \
  --terminate-existing \
  "$bundle_id"

printf '%s\n' '==> OpenGroove is installed and launched'
