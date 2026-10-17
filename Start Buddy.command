#!/bin/bash
set -euo pipefail
task_root="$(cd "$(dirname "$0")" && pwd)"
exec /bin/bash "$task_root/outputs/tv-companion/start.command"
