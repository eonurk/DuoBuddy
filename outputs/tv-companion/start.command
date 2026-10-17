#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
exec /usr/bin/caffeinate -i /usr/local/bin/node server.mjs
