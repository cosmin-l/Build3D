#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p out
javac -d out -encoding UTF-8 src/build3d/*.java
echo "Build OK -> out/"
