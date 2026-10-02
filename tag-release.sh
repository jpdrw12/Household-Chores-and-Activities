#!/usr/bin/env bash
# Tags the current commit as v<versionName> (read from app/build.gradle.kts) and pushes the tag,
# which triggers .github/workflows/release.yml to build a signed release APK and attach it to a
# matching GitHub Release.
#
# Run this AFTER committing a version bump (./bump-version.sh [patch|minor|major]) — it tags
# whatever commit is currently checked out, it doesn't bump anything itself.
#
# Usage: ./tag-release.sh
set -euo pipefail

GRADLE_FILE="$(dirname "$0")/app/build.gradle.kts"
VERSION=$(grep -oP 'versionName = "\K[^"]+' "$GRADLE_FILE")
TAG="v$VERSION"

if git rev-parse "$TAG" >/dev/null 2>&1; then
    echo "Tag $TAG already exists." >&2
    exit 1
fi

if [ -n "$(git status --porcelain)" ]; then
    echo "Working tree isn't clean — commit or stash first." >&2
    exit 1
fi

git tag -a "$TAG" -m "Release $TAG"
git push origin "$TAG"

echo "Tagged and pushed $TAG — check the Actions tab for the release build:"
echo "https://github.com/jpdrw12/Household-Chores-and-Activities/actions"
