#!/bin/sh
# Scrubs private values from stdin before anything is shown, logged, committed
# or posted to the (public) GitHub repo. Pipe every TV output through this.
#   - the TV IP and the Netflix ESN from .env (exact values)
#   - anything that looks like an ESN (NFANDROID…/NFCDxx-…)
#   - every IPv4 address except 127.0.0.1 / 0.0.0.0
#   - MAC addresses, the device serial, the Android ID
# Usage: <cmd> | scripts/redact.sh
set -u
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
ENV_FILE="$ROOT/.env"

envval() {
  [ -f "$ENV_FILE" ] || return 0
  grep -E "^$1=" "$ENV_FILE" | head -n1 | cut -d= -f2- | tr -d '\r"'"'"' '
}

REDACT_IP=$(envval tv_private_ip_address) \
REDACT_ESN=$(envval netflix_esn) \
REDACT_SERIAL=${REDACT_SERIAL:-} \
REDACT_ANDROID_ID=${REDACT_ANDROID_ID:-} \
exec perl -pe '
  BEGIN {
    @fixed = grep { length } map { $ENV{$_} // "" }
      qw(REDACT_ESN REDACT_IP REDACT_SERIAL REDACT_ANDROID_ID);
    %label = (
      ($ENV{REDACT_ESN} // "")        => "<netflix-esn>",
      ($ENV{REDACT_IP} // "")         => "<tv-ip>",
      ($ENV{REDACT_SERIAL} // "")     => "<serial>",
      ($ENV{REDACT_ANDROID_ID} // "") => "<android-id>",
    );
  }
  for my $s (@fixed) { s/\Q$s\E/$label{$s}/g; }
  s/\bNF[A-Z0-9]{2,}[-_][A-Za-z0-9=_\-~.]{8,}/<netflix-esn>/g;
  s/\b(?!127\.0\.0\.1\b)(?!0\.0\.0\.0\b)(?:\d{1,3}\.){3}\d{1,3}\b/<ip>/g;
  s/\b(?:[0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}\b/<mac>/g;
'
