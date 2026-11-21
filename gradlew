#!/usr/bin/env sh
set -eu
VERSION=8.9
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
GRADLE_USER_HOME="${SCRIPT_DIR}/.gradle-user-home"
GRADLE_HOME="$GRADLE_USER_HOME/bootstrap/gradle-$VERSION"
export GRADLE_USER_HOME
if [ ! -x "$GRADLE_HOME/bin/gradle" ]; then
  mkdir -p "$GRADLE_USER_HOME/bootstrap"
  ZIP="${TMPDIR:-/tmp}/gradle-$VERSION-bin.zip"
  if command -v curl >/dev/null 2>&1; then curl -fL "https://services.gradle.org/distributions/gradle-$VERSION-bin.zip" -o "$ZIP"; else wget "https://services.gradle.org/distributions/gradle-$VERSION-bin.zip" -O "$ZIP"; fi
  unzip -oq "$ZIP" -d "$GRADLE_USER_HOME/bootstrap"
  rm -f "$ZIP"
fi
exec "$GRADLE_HOME/bin/gradle" "$@"
