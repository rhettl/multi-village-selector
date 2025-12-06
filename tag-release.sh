#!/bin/bash
# tag-release.sh - Create a release tag with matching gradle version
#
# Usage: ./tag-release.sh <version> [-m "commit message"]
# Example: ./tag-release.sh 0.4.0-RC1
# Example: ./tag-release.sh 0.4.0 -m "Release v0.4.0 with placement control"

set -e

VERSION="$1"
if [ -z "$VERSION" ]; then
    echo "Usage: ./tag-release.sh <version> [-m \"commit message\"]"
    echo "Example: ./tag-release.sh 0.4.0-RC1"
    exit 1
fi

# Strip leading 'v' if provided (we add it ourselves)
VERSION="${VERSION#v}"
shift

# Parse optional -m argument
COMMIT_MSG="Bump version to $VERSION"
while [[ $# -gt 0 ]]; do
    case $1 in
        -m)
            COMMIT_MSG="$2"
            shift 2
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

# Get current branch and remote
BRANCH=$(git rev-parse --abbrev-ref HEAD)
REMOTE=$(git remote | head -1)

echo "📦 Releasing v$VERSION"
echo "   Branch: $BRANCH"
echo "   Remote: $REMOTE"
echo "   Commit: $COMMIT_MSG"
echo ""

# Update gradle.properties
sed -i '' "s/^mod_version=.*/mod_version=$VERSION/" gradle.properties
echo "✓ Updated gradle.properties"

# Commit and tag
git add gradle.properties
git commit -m "$COMMIT_MSG"
echo "✓ Committed"

git tag -a "v$VERSION" -m "v$VERSION"
echo "✓ Tagged v$VERSION"

# Push branch and tag
git push "$REMOTE" "$BRANCH"
git push "$REMOTE" "v$VERSION"
echo ""
echo "✅ Released v$VERSION"
echo "   View: https://github.com/rhettl/multi-village-selector/actions"
