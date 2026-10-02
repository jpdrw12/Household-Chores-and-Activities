#!/usr/bin/env bash
# Bumps versionName (semver) and versionCode (+1) in app/build.gradle.kts.
#
# Usage: ./bump-version.sh [patch|minor|major]   (default: patch)
#
# This only edits build.gradle.kts — it does NOT commit, tag, or touch CHANGELOG.md. Add a
# matching entry to CHANGELOG.md (move [Unreleased] items under a new version heading) before
# committing the bump.
set -euo pipefail

BUMP_TYPE="${1:-patch}"
GRADLE_FILE="$(dirname "$0")/app/build.gradle.kts"

CURRENT_VERSION=$(grep -oP 'versionName = "\K[^"]+' "$GRADLE_FILE")
CURRENT_CODE=$(grep -oP 'versionCode = \K[0-9]+' "$GRADLE_FILE")

IFS='.' read -r MAJOR MINOR PATCH <<< "$CURRENT_VERSION"

case "$BUMP_TYPE" in
    major) MAJOR=$((MAJOR + 1)); MINOR=0; PATCH=0 ;;
    minor) MINOR=$((MINOR + 1)); PATCH=0 ;;
    patch) PATCH=$((PATCH + 1)) ;;
    *) echo "Usage: $0 [patch|minor|major]" >&2; exit 1 ;;
esac

NEW_VERSION="$MAJOR.$MINOR.$PATCH"
NEW_CODE=$((CURRENT_CODE + 1))

sed -i \
    -e "s/versionName = \"$CURRENT_VERSION\"/versionName = \"$NEW_VERSION\"/" \
    -e "s/versionCode = $CURRENT_CODE/versionCode = $NEW_CODE/" \
    "$GRADLE_FILE"

echo "Bumped $CURRENT_VERSION (code $CURRENT_CODE) -> $NEW_VERSION (code $NEW_CODE)"
echo "Next: add a [$NEW_VERSION] section to CHANGELOG.md, then commit both files together."
