#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "$0")/.." && pwd)"
build_dir="$project_dir/build"
jar_dir="$build_dir/libs"
distribution_dir="$build_dir/distributions"
staging_dir="$build_dir/staging/AeroVista"
version="1.1.0"

python3 "$project_dir/scripts/validate_theme.py"

if [[ "$build_dir" != "$project_dir/build" || -z "$project_dir" ]]; then
  echo "Refusing to clean an unexpected build directory" >&2
  exit 1
fi

rm -rf -- "$build_dir"
mkdir -p "$jar_dir" "$distribution_dir" "$staging_dir/lib"

(
  cd "$project_dir/src/main/resources"
  jar --create --file "$jar_dir/AeroVista-$version.jar" .
)

cp "$jar_dir/AeroVista-$version.jar" "$staging_dir/lib/"
(
  cd "$build_dir/staging"
  zip -q -r "$distribution_dir/AeroVista-$version.zip" AeroVista
)

echo "Built $distribution_dir/AeroVista-$version.zip"
