#!/bin/sh
# Halves the three system animation durations. Idempotent.
set -eu
HERE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
TV="$HERE/../../scripts/tv.sh"
SCALE=${SCALE:-0.5}

sh "$TV" shell "for k in window_animation_scale transition_animation_scale animator_duration_scale; do settings put global \$k $SCALE; done"
sh "$HERE/check.sh"
