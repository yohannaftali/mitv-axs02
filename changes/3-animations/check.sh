#!/bin/sh
# Exit 0 when all three animation scales are 0.5 (or $SCALE).
set -eu
HERE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
TV="$HERE/../../scripts/tv.sh"
SCALE=${SCALE:-0.5}

vals=$(sh "$TV" shell 'for k in window_animation_scale transition_animation_scale animator_duration_scale; do settings get global $k; done' | tr -d '\r' | tr '\n' ' ')
if [ "$vals" = "$SCALE $SCALE $SCALE " ]; then
  echo "ok: animation scales $SCALE"; exit 0
fi
echo "not applied: scales are $vals"; exit 1
