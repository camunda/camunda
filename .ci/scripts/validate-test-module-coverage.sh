#!/bin/bash
#
# Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
# one or more contributor license agreements. See the NOTICE file distributed
# with this work for additional information regarding copyright ownership.
# Licensed under the Camunda License 1.0. You may not use this file
# except in compliance with the Camunda License 1.0.
#

set -euo pipefail

# ==============================================================================
# Validate Test Module Coverage
#
# This script validates that all zeebe modules are covered by test constants.
# It compares Maven's module structure with the test constants defined in the
# setup-tests job of the CI workflow.
#
# Usage:
#   ./validate-test-module-coverage.sh <IT_DISTRIBUTED> <IT_EXPORTER> <IT_ENGINE>
#
# Arguments:
#   IT_DISTRIBUTED - Comma-separated list of distributed modules (from setup-tests output)
#   IT_EXPORTER    - Comma-separated list of exporter modules (from setup-tests output)
#   IT_ENGINE      - Comma-separated list of engine modules (from setup-tests output)
#
# Example:
#   ./validate-test-module-coverage.sh \
#     "':zeebe-broker',':zeebe-backup'" \
#     "':zeebe-elasticsearch-exporter'" \
#     "':zeebe-auth',':zeebe-protocol'"
# ==============================================================================

# Check arguments
if [ $# -ne 3 ]; then
  echo "Usage: $0 <IT_DISTRIBUTED> <IT_EXPORTER> <IT_ENGINE>"
  echo ""
  echo "This script validates test module coverage by comparing Maven modules with test constants."
  exit 1
fi

IT_DISTRIBUTED="$1"
IT_EXPORTER="$2"
IT_ENGINE="$3"

echo "🔍 Validating test module coverage..."
echo ""
echo "📍 Working directory: $(pwd)"
echo ""

# ==============================================================================
# Read constants from arguments
# ==============================================================================

# Extract module artifactIds from constants (remove quotes, commas, and : prefix)
ALL_IT_MODULES="${IT_DISTRIBUTED},${IT_EXPORTER},${IT_ENGINE}"
ALL_COVERED=$(echo "$ALL_IT_MODULES" | tr ',' '\n' | tr -d "'" | sed 's/^://' | sort -u)

echo "📊 Module artifactIds in test constants ($(echo "$ALL_COVERED" | wc -l | xargs)):"
echo "$ALL_COVERED" | awk '{print "  - " $0}'
echo ""

# ==============================================================================
# Use Maven to get all zeebe module artifactIds
# ==============================================================================

echo "🔎 Using Maven to discover zeebe modules..."
echo ""

# Change to zeebe directory (handle both relative paths)
if [ -d "zeebe" ]; then
  cd zeebe
else
  echo "❌ ERROR: zeebe directory not found in $(pwd)"
  exit 1
fi

# Use Maven to list all project artifactIds
echo "Running: mvnw exec:exec to get all artifactIds..."
echo "This may take a few minutes while Maven resolves the reactor..."
echo ""

echo "Starting Maven execution at $(date '+%Y-%m-%d %H:%M:%S')"
ZEEBE_MODULES=$(../mvnw -q \
  --also-make \
  --projects '!qa/integration-tests,!qa/update-tests' \
  exec:exec \
  -Dexec.executable=echo \
  -Dexec.args="\${project.artifactId}" \
  2>&1 | grep -v '^\[' | grep -v '^Downloading' | grep -v '^Downloaded' | grep -v '^Progress' | sort -u | xargs)

echo "Maven execution completed at $(date '+%Y-%m-%d %H:%M:%S')"
echo ""

cd ..

if [ -z "$ZEEBE_MODULES" ]; then
  echo "❌ ERROR: Maven returned no modules!"
  echo "This might indicate:"
  echo "  - Maven failed to execute"
  echo "  - No modules matched the project selector"
  echo "  - All output was filtered out"
  exit 1
fi

echo "📂 Module artifactIds discovered by Maven ($(echo "$ZEEBE_MODULES" | wc -w | xargs)):"
for m in $ZEEBE_MODULES; do
  echo "  - $m"
done
echo ""

# ==============================================================================
# Validate coverage - direct comparison, no transformations
# ==============================================================================

echo "🔬 Validating coverage (direct artifactId comparison)..."
echo ""

# Modules to exclude from validation (parent POMs, build tools, QA utilities)
EXCLUDED="zeebe-qa-integration-tests zeebe-qa-update-tests zeebe-qa-util zeebe-parent zeebe-build-tools zeebe-bom zeebe-root zeebe-atomix-parent zeebe-qa zeebe-feel-tagged-parameters"

MISSING_MODULES=""
EXTRA_MODULES=""

# Check for missing modules (in Maven but not in test constants)
for module in $ZEEBE_MODULES; do
  # Skip excluded modules
  if echo "$EXCLUDED" | grep -qw "$module"; then
    continue
  fi

  # Check if module is in test constants
  if ! echo "$ALL_COVERED" | grep -qx "$module"; then
    MISSING_MODULES="${MISSING_MODULES}${module} "
  fi
done

# Check for extra modules (in test constants but not in Maven)
for covered in $ALL_COVERED; do
  if ! echo "$ZEEBE_MODULES" | grep -qw "$covered"; then
    EXTRA_MODULES="${EXTRA_MODULES}${covered} "
  fi
done

# ==============================================================================
# Report results
# ==============================================================================

if [ -n "$MISSING_MODULES" ]; then
  echo "❌ ERROR: The following Maven modules are NOT in test constants:"
  for m in $MISSING_MODULES; do
    echo "  - $m"
  done
  echo ""
  echo "Add these to the appropriate constant in 'Set all test constants' step."
  exit 1
fi

if [ -n "$EXTRA_MODULES" ]; then
  echo "⚠️  WARNING: The following modules are in test constants but NOT in zeebe/ directory:"
  for m in $EXTRA_MODULES; do
    echo "  - $m"
  done
  echo ""
  echo "Note: These modules exist outside zeebe/ (e.g., clients/, load-tests/)."
  echo "      They are configured in test constants but not validated by this zeebe-only check."
  echo ""
fi

# Success!
ZEEBE_MODULE_COUNT=$(echo "$ZEEBE_MODULES" | wc -w | xargs)
EXCLUDED_COUNT=$(echo "$EXCLUDED" | wc -w | xargs)
COVERED_COUNT=$(echo "$ALL_COVERED" | wc -l | xargs)
NON_ZEEBE_COUNT=$(echo "$EXTRA_MODULES" | wc -w | xargs)

echo "✅ All zeebe/ directory modules are covered by test constants!"
echo ""
echo "📈 Summary:"
echo "  - Zeebe modules found: $ZEEBE_MODULE_COUNT"
echo "  - Excluded modules: $EXCLUDED_COUNT"
echo "  - Covered by tests: $COVERED_COUNT"
if [ "$NON_ZEEBE_COUNT" -gt 0 ]; then
  echo "  - Non-zeebe/ modules: $NON_ZEEBE_COUNT (not validated)"
fi
echo ""
echo "🎉 Validation passed!"
