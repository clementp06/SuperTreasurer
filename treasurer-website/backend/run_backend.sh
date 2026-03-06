#!/bin/bash
set -e
cd "$(dirname "$0")"
exec .venv/bin/gunicorn -w 2 -b 0.0.0.0:5000 server:app