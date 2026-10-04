#!/usr/bin/env bash
set -euo pipefail

# Quick-start script to initialize git and prepare for GitHub push
echo "==> Initializing Git repository for Member 3: Ride Management Service..."

if [ -d ".git" ]; then
    echo "Git repository is already initialized."
else
    git init
    git branch -M main
    git add .
    git commit -m "feat: initial commit for Member 3 Ride Management Service"
    echo "==> Repository initialized and initial commit created on 'main' branch."
fi

echo ""
echo "Next Steps:"
echo "1. Create a repository on GitHub (e.g., https://github.com/your-username/IT3130_Member3_RideManagementService)"
echo "2. Run: git remote add origin https://github.com/<your-username>/IT3130_Member3_RideManagementService.git"
echo "3. Run: git push -u origin main"
echo "4. Create feature branch: git checkout -b feature/member3-ride-orchestrator"
