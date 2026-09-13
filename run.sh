#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
./build.sh
java -cp out build3d.Main "${1:-maps/sample.map}"
