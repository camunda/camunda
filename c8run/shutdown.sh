#!/bin/bash

BASEDIR=$(dirname "$0")
runScript="$BASEDIR/internal/run.sh"

# Start the application in the background
exec "$runScript" stop
