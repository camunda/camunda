#!/bin/bash
# Joke Generator 3000 -- Basic Auth Test Suite
# Tests the app running with --profiles=basic against the manual test plan
# (Test Suite 1: Basic Auth Profile + Test Suite 3: Security Boundary Validation)
#
# Prerequisites:
#   1. PostgreSQL running on port 5432:  docker compose up -d postgres
#   2. App running on port 8080:         ./mvnw spring-boot:run -Dspring-boot.run.profiles=basic
#
# Usage:
#   chmod +x tests/test-basic-auth.sh
#   ./tests/test-basic-auth.sh

set -euo pipefail

BASE_URL="http://localhost:8080"
PASS=0
FAIL=0
TOTAL=0

# Temp files for cookie jars
COOKIE_JAR_USER=$(mktemp)
COOKIE_JAR_ADMIN=$(mktemp)
COOKIE_JAR_LOGOUT=$(mktemp)
HEADER_DUMP=$(mktemp)

cleanup() {
  rm -f "$COOKIE_JAR_USER" "$COOKIE_JAR_ADMIN" "$COOKIE_JAR_LOGOUT" "$HEADER_DUMP"
}
trap cleanup EXIT

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

run_test() {
  local test_id="$1"
  local description="$2"
  local expected="$3"
  local actual="$4"
  TOTAL=$((TOTAL + 1))
  if [ "$expected" = "$actual" ]; then
    echo "  PASS  $test_id: $description"
    PASS=$((PASS + 1))
  else
    echo "  FAIL  $test_id: $description (expected: $expected, got: $actual)"
    FAIL=$((FAIL + 1))
  fi
}

run_test_contains() {
  local test_id="$1"
  local description="$2"
  local needle="$3"
  local haystack="$4"
  TOTAL=$((TOTAL + 1))
  if echo "$haystack" | grep -qi "$needle"; then
    echo "  PASS  $test_id: $description"
    PASS=$((PASS + 1))
  else
    echo "  FAIL  $test_id: $description (expected response to contain: $needle)"
    FAIL=$((FAIL + 1))
  fi
}

# ---------------------------------------------------------------------------
# Prerequisite checks
# ---------------------------------------------------------------------------

echo "Checking prerequisites..."

# Check PostgreSQL
if ! nc -z localhost 5432 2>/dev/null; then
  echo ""
  echo "ERROR: PostgreSQL is not running on port 5432."
  echo "  Start it with:  docker compose up -d postgres"
  echo ""
  exit 1
fi

# Check App
if ! curl -s -o /dev/null --max-time 3 "$BASE_URL/" 2>/dev/null; then
  echo ""
  echo "ERROR: App is not running on port 8080."
  echo "  Start it with:  ./mvnw spring-boot:run -Dspring-boot.run.profiles=basic"
  echo ""
  exit 1
fi

echo "Prerequisites OK."
echo ""
echo "=========================================="
echo " Basic Auth Test Suite"
echo "=========================================="
echo ""

# ---------------------------------------------------------------------------
# Test Suite 1: Basic Auth Profile
# ---------------------------------------------------------------------------

echo "--- Public Access ---"

# BASIC-001: Landing page accessible without auth
STATUS=$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/")
run_test "BASIC-001" "Landing page / returns 200 without auth" "200" "$STATUS"

# BASIC-002: Static assets accessible without auth
STATUS=$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/css/style.css")
run_test "BASIC-002" "Static asset /css/style.css returns 200 without auth" "200" "$STATUS"

# BASIC-003: /jokes without auth redirects to /login (302)
STATUS=$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/jokes")
run_test "BASIC-003" "/jokes without auth returns 302 redirect" "302" "$STATUS"

# BASIC-004: GET /login returns 200
STATUS=$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/login")
run_test "BASIC-004" "GET /login returns 200" "200" "$STATUS"

echo ""
echo "--- User Login & Session ---"

# BASIC-005: Login with valid credentials returns 204
STATUS=$(curl -s -o /dev/null -w '%{http_code}' \
  -c "$COOKIE_JAR_USER" \
  -X POST \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=user&password=password" \
  "$BASE_URL/login")
run_test "BASIC-005" "Login with valid credentials (user/password) returns 204" "204" "$STATUS"

# BASIC-006: Login with invalid credentials returns non-2xx
STATUS=$(curl -s -o /dev/null -w '%{http_code}' \
  -X POST \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=user&password=wrongpassword" \
  "$BASE_URL/login")
# Accept any non-2xx status as a pass
TOTAL=$((TOTAL + 1))
if [ "${STATUS:0:1}" != "2" ]; then
  echo "  PASS  BASIC-006: Login with invalid credentials returns non-2xx (got: $STATUS)"
  PASS=$((PASS + 1))
else
  echo "  FAIL  BASIC-006: Login with invalid credentials returns non-2xx (expected: non-2xx, got: $STATUS)"
  FAIL=$((FAIL + 1))
fi

# BASIC-007: After login as user, /jokes returns 200
STATUS=$(curl -s -o /dev/null -w '%{http_code}' \
  -b "$COOKIE_JAR_USER" \
  "$BASE_URL/jokes")
run_test "BASIC-007" "After login as user, /jokes returns 200" "200" "$STATUS"

# BASIC-008: After login as user, /api/jokes/random returns 200 with JSON
RESPONSE=$(curl -s -w '\n%{http_code}' \
  -b "$COOKIE_JAR_USER" \
  "$BASE_URL/api/jokes/random")
BODY=$(echo "$RESPONSE" | sed '$d')
STATUS=$(echo "$RESPONSE" | tail -1)
run_test "BASIC-008" "After login as user, /api/jokes/random returns 200" "200" "$STATUS"

# BASIC-009: After login as user, /jokes/admin returns 403
STATUS=$(curl -s -o /dev/null -w '%{http_code}' \
  -b "$COOKIE_JAR_USER" \
  "$BASE_URL/jokes/admin")
run_test "BASIC-009" "After login as user, /jokes/admin returns 403" "403" "$STATUS"

# BASIC-010: After login as user, POST /api/jokes/generate returns 403
STATUS=$(curl -s -o /dev/null -w '%{http_code}' \
  -b "$COOKIE_JAR_USER" \
  -X POST \
  -H "Content-Type: application/json" \
  -d '{"setup":"Test joke","punchline":"Test punchline","category":"general"}' \
  "$BASE_URL/api/jokes/generate")
run_test "BASIC-010" "After login as user, POST /api/jokes/generate returns 403" "403" "$STATUS"

echo ""
echo "--- Admin Login & Session ---"

# BASIC-011: Login as admin returns 204
STATUS=$(curl -s -o /dev/null -w '%{http_code}' \
  -c "$COOKIE_JAR_ADMIN" \
  -X POST \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=admin&password=password" \
  "$BASE_URL/login")
run_test "BASIC-011" "Login as admin (admin/password) returns 204" "204" "$STATUS"

# BASIC-012: After login as admin, /jokes/admin returns 200
STATUS=$(curl -s -o /dev/null -w '%{http_code}' \
  -b "$COOKIE_JAR_ADMIN" \
  "$BASE_URL/jokes/admin")
run_test "BASIC-012" "After login as admin, /jokes/admin returns 200" "200" "$STATUS"

# BASIC-013: After login as admin, POST /api/jokes/generate returns 201
STATUS=$(curl -s -o /dev/null -w '%{http_code}' \
  -b "$COOKIE_JAR_ADMIN" \
  -X POST \
  -H "Content-Type: application/json" \
  -d '{"setup":"Test joke","punchline":"Test punchline","category":"general"}' \
  "$BASE_URL/api/jokes/generate")
run_test "BASIC-013" "After login as admin, POST /api/jokes/generate returns 201" "201" "$STATUS"

echo ""
echo "--- Logout ---"

# BASIC-014: After logout, /jokes redirects to login again
# First login, then logout, then check /jokes
curl -s -o /dev/null \
  -c "$COOKIE_JAR_LOGOUT" \
  -X POST \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=user&password=password" \
  "$BASE_URL/login"

# Logout
curl -s -o /dev/null \
  -b "$COOKIE_JAR_LOGOUT" \
  -c "$COOKIE_JAR_LOGOUT" \
  -X POST \
  "$BASE_URL/logout"

# Check /jokes after logout -- should redirect to login
STATUS=$(curl -s -o /dev/null -w '%{http_code}' \
  -b "$COOKIE_JAR_LOGOUT" \
  "$BASE_URL/jokes")
run_test "BASIC-014" "After logout, /jokes redirects to login (302)" "302" "$STATUS"

echo ""
echo "=========================================="
echo " Security Boundary Validation"
echo "=========================================="
echo ""

# SEC-001: /api/jokes/random without auth returns 401
STATUS=$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/api/jokes/random")
run_test "SEC-001" "/api/jokes/random without auth returns 401" "401" "$STATUS"

# SEC-002: Non-existent path returns 404
STATUS=$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/nonexistent")
# The catch-all deny chain may return 404 or 401/403 -- per test plan, just verify not 200
TOTAL=$((TOTAL + 1))
if [ "$STATUS" = "404" ]; then
  echo "  PASS  SEC-002: Non-existent path /nonexistent returns 404"
  PASS=$((PASS + 1))
elif [ "$STATUS" != "200" ]; then
  echo "  PASS  SEC-002: Non-existent path /nonexistent returns non-200 (got: $STATUS)"
  PASS=$((PASS + 1))
else
  echo "  FAIL  SEC-002: Non-existent path /nonexistent should not return 200 (got: $STATUS)"
  FAIL=$((FAIL + 1))
fi

# SEC-003: Security headers present
HEADERS=$(curl -s -D - -o /dev/null "$BASE_URL/")

# Check X-Content-Type-Options
run_test_contains "SEC-003a" "X-Content-Type-Options header present" "X-Content-Type-Options" "$HEADERS"

# Check X-Frame-Options
run_test_contains "SEC-003b" "X-Frame-Options header present" "X-Frame-Options" "$HEADERS"

# ---------------------------------------------------------------------------
# Results
# ---------------------------------------------------------------------------

echo ""
echo "=========================================="
echo " Results: $PASS passed, $FAIL failed, $TOTAL total"
echo "=========================================="

if [ "$FAIL" -gt 0 ]; then
  exit 1
fi
