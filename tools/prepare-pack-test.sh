#!/usr/bin/env bash
# Copies a Bubie Pack server pack (mods and configs) into runs/pack-test for ./gradlew runPackGameTest, which runs
# the Bubie game tests (PackCompatGameTests included) with the pack's mods loaded.
# Left out: Jade and Base Blueprint (the dev run already loads them), any Bubie jar, and Integrated Dynamics with its addons (a dev run
# registers every mod's game tests, and theirs reference Refined Storage, which the pack lacks).
# usage: tools/prepare-pack-test.sh [path/to/server-pack]   (default: newest ../minecraft-modpack/dist/server-*)
set -euo pipefail
cd "$(dirname "$0")/.."
src="${1:-$(ls -d ../minecraft-modpack/dist/server-* | sort -V | tail -1)}"
dst=runs/pack-test
rm -rf "$dst/mods" "$dst/config" "$dst/defaultconfigs" "$dst/kubejs"
mkdir -p "$dst/mods"
for dir in config defaultconfigs kubejs; do
  [ -d "$src/$dir" ] && cp -r "$src/$dir" "$dst/"
done
# The pack's Bubie server config turns on advancedJobsNeedChip; the job tests expect the mod's defaults (the chip gate
# has a test of its own).
rm -f "$dst/defaultconfigs/bubie-server.toml"
for jar in "$src"/mods/*.jar; do
  case "$(basename "$jar")" in
    Jade-*|bubie-*|baseblueprint-*|integrated*) ;;
    *) cp "$jar" "$dst/mods/" ;;
  esac
done
echo "Server pack: $src"
echo "Copied $(ls "$dst/mods" | wc -l) mods into $dst/mods"
