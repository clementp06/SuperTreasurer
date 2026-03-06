#!/bin/bash
set -e
cd "$(dirname "$0")"
exec .venv/bin/gunicorn -w 2 -b 127.0.0.1:5000 server:app