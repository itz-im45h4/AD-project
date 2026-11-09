#!/usr/bin/env bash
set -euo pipefail

# IT3130 - RideLink Member Submissions Packager
# Generates separated, clean directories and ZIP files for each group member.
# Removes all other members' services, runbooks, and temporary/secret files.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PYTHON_SCRIPT="$SCRIPT_DIR/package-member-submissions.py"

echo "=========================================================="
echo " RideLink — Member Submission Packager (IT3130)"
echo "=========================================================="

if ! command -v python3 &>/dev/null; then
    echo "Error: python3 is required to run this packaging script." >&2
    exit 1
fi

python3 "$PYTHON_SCRIPT" "$@"

echo "==> Done! Review the packages in: $SCRIPT_DIR/submissions"
