#!/bin/bash
set -euo pipefail

# Drop the database, forcefully terminating any connections
psql postgres -c "DROP DATABASE IF EXISTS \"${PGDATABASE}\" WITH (FORCE);"

