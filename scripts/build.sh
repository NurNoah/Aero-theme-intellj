#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "$0")/.." && pwd)"

cd "$project_dir"
env -u JAVA_HOME ./gradlew clean buildPlugin
