#!/usr/bin/env bash
set -euo pipefail

# IT3130 Submission Packaging Script
# Complies with assignment brief §9:
# - Recommended file name format: IT3130_GroupXX_RideLink.zip
# - Excludes build outputs, local credentials/passwords, and version control directories

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PACKAGE_DIR="/tmp/ridelink-submission-package"
OUTPUT_DIR="$PROJECT_ROOT/plan/share-zips"

echo "==> Preparing clean submission archive..."
rm -rf "$PACKAGE_DIR"
mkdir -p "$PACKAGE_DIR" "$OUTPUT_DIR"

# Copy source tree excluding build artifacts, local secrets, and IDE metadata
rsync -a \
  --exclude='.git/' \
  --exclude='.github/' \
  --exclude='plan/' \
  --exclude='**/target/' \
  --exclude='.idea/' \
  --exclude='.vscode/' \
  --exclude='*.iml' \
  --exclude='*.log' \
  --exclude='application-local.properties' \
  --exclude='application-local.yml' \
  --exclude='.env' \
  --exclude='.env.*' \
  "$PROJECT_ROOT/" "$PACKAGE_DIR/"

cd "$PACKAGE_DIR"

# 1. Standard Courseweb submission archive
jar --create --no-manifest \
  --file "$OUTPUT_DIR/IT3130_GroupXX_RideLink.zip" \
  .

# 2. Local development share archive
cp "$OUTPUT_DIR/IT3130_GroupXX_RideLink.zip" "$OUTPUT_DIR/RideLink-working-local.zip"

echo "==> Packaging complete!"
echo "    Submission Zip : $OUTPUT_DIR/IT3130_GroupXX_RideLink.zip"
echo "    Local Share Zip: $OUTPUT_DIR/RideLink-working-local.zip"
