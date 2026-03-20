#!/bin/bash
# Joke Generator 3000 -- OIDC Test Suite
# Automates Test Suite 2 (OIDC Profile) from TEST-PLAN.md
#
# Usage: ./tests/test-oidc.sh
# Prerequisites:
#   docker compose up -d
#   ./mvnw spring-boot:run -Dspring-boot.run.profiles=oidc

set -euo pipefail

# --- Configuration ---
PASS=0
FAIL=0
TOTAL=0
KEYCLOAK_URL="http://localhost:8180"
APP_URL="http://localhost:8080"
REALM="joke-generator"
CLIENT_ID="joke-generator-app"
CLIENT_SECRET="joke-generator-secret"

# --- Helpers ---

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

get_token() {
  local username="$1"
  local password="$2"
  curl -s -X POST "$KEYCLOAK_URL/realms/$REALM/protocol/openid-connect/token" \
    -d "grant_type=password&client_id=$CLIENT_ID&client_secret=$CLIENT_SECRET&username=$username&password=$password" \
    -H "Content-Type: application/x-www-form-urlencoded" | jq -r '.access_token'
}

# --- Prerequisite Checks ---

echo "============================================"
echo "  Joke Generator 3000 -- OIDC Test Suite"
echo "============================================"
echo ""
echo "Checking prerequisites..."

PREREQ_FAIL=0

# Check jq is installed
if ! command -v jq &>/dev/null; then
  echo "  ERROR: jq is not installed. Install it with: brew install jq"
  PREREQ_FAIL=1
fi

# Check curl is installed
if ! command -v curl &>/dev/null; then
  echo "  ERROR: curl is not installed."
  PREREQ_FAIL=1
fi

# Check Postgres on port 5432
if ! curl -s --max-time 2 "http://localhost:5432" &>/dev/null && ! pg_isready -h localhost -p 5432 &>/dev/null; then
  # pg_isready is the proper check; fallback to nc
  if ! nc -z localhost 5432 2>/dev/null; then
    echo "  ERROR: PostgreSQL is not running on port 5432."
    echo "         Start it with: docker compose up -d postgres"
    PREREQ_FAIL=1
  fi
fi

# Check Keycloak on port 8180
KC_STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 "$KEYCLOAK_URL" 2>/dev/null || echo "000")
if [ "$KC_STATUS" = "000" ]; then
  echo "  ERROR: Keycloak is not running on port 8180."
  echo "         Start it with: docker compose up -d"
  echo "         Wait ~30 seconds for Keycloak to start."
  PREREQ_FAIL=1
fi

# Check App on port 8080
APP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 "$APP_URL/" 2>/dev/null || echo "000")
if [ "$APP_STATUS" = "000" ]; then
  echo "  ERROR: Application is not running on port 8080."
  echo "         Start it with: ./mvnw spring-boot:run -Dspring-boot.run.profiles=oidc"
  PREREQ_FAIL=1
fi

if [ "$PREREQ_FAIL" -ne 0 ]; then
  echo ""
  echo "Prerequisites not met. Aborting."
  exit 1
fi

echo "  All prerequisites OK."
echo ""
echo "--------------------------------------------"
echo "  Running OIDC tests..."
echo "--------------------------------------------"
echo ""

# --- OIDC-001: Landing page returns 200 without auth ---
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$APP_URL/")
run_test "OIDC-001" "Landing page / returns 200 without auth" "200" "$STATUS"

# --- OIDC-002: /jokes without auth returns 302 (redirect to Keycloak) ---
STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 "$APP_URL/jokes")
run_test "OIDC-002" "/jokes without auth returns 302 redirect" "302" "$STATUS"

# --- OIDC-003: Get token for regular user from Keycloak ---
USER_TOKEN=$(get_token "user" "password")
if [ -n "$USER_TOKEN" ] && [ "$USER_TOKEN" != "null" ]; then
  TOKEN_RESULT="valid"
else
  TOKEN_RESULT="empty"
fi
run_test "OIDC-003" "Get token for regular user from Keycloak" "valid" "$TOKEN_RESULT"

# --- OIDC-004: GET /api/jokes/random with Bearer token returns 200 ---
RESPONSE=$(curl -s -w "\n%{http_code}" -H "Authorization: Bearer $USER_TOKEN" "$APP_URL/api/jokes/random")
STATUS=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
run_test "OIDC-004" "GET /api/jokes/random with Bearer token returns 200" "200" "$STATUS"

# Verify response is JSON with expected fields
if echo "$BODY" | jq -e '.id and .setup and .punchline and .category' &>/dev/null; then
  JSON_RESULT="valid"
else
  JSON_RESULT="invalid"
fi
run_test "OIDC-004b" "Random joke response contains id, setup, punchline, category" "valid" "$JSON_RESULT"

# --- OIDC-005: GET /api/jokes/random without token returns 401 ---
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$APP_URL/api/jokes/random")
run_test "OIDC-005" "GET /api/jokes/random without token returns 401" "401" "$STATUS"

# --- OIDC-006: POST /api/jokes/generate with regular user token returns 403 ---
STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  -X POST \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"setup":"OIDC test joke","punchline":"OIDC punchline","category":"programming"}' \
  "$APP_URL/api/jokes/generate")
run_test "OIDC-006" "POST /api/jokes/generate with regular user returns 403" "403" "$STATUS"

# --- OIDC-007: Get token for admin user from Keycloak ---
ADMIN_TOKEN=$(get_token "admin" "password")
if [ -n "$ADMIN_TOKEN" ] && [ "$ADMIN_TOKEN" != "null" ]; then
  ADMIN_TOKEN_RESULT="valid"
else
  ADMIN_TOKEN_RESULT="empty"
fi
run_test "OIDC-007" "Get token for admin user from Keycloak" "valid" "$ADMIN_TOKEN_RESULT"

# --- OIDC-008: POST /api/jokes/generate with admin token returns 201 ---
RESPONSE=$(curl -s -w "\n%{http_code}" \
  -X POST \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"setup":"OIDC test joke","punchline":"OIDC punchline","category":"programming"}' \
  "$APP_URL/api/jokes/generate")
STATUS=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
run_test "OIDC-008" "POST /api/jokes/generate with admin token returns 201" "201" "$STATUS"

# Verify the created joke JSON
if echo "$BODY" | jq -e '.id and .setup and .punchline and .category' &>/dev/null; then
  CREATE_RESULT="valid"
else
  CREATE_RESULT="invalid"
fi
run_test "OIDC-008b" "Created joke response contains id, setup, punchline, category" "valid" "$CREATE_RESULT"

# --- OIDC-009: GET /api/jokes/random with invalid token returns 401 ---
STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  -H "Authorization: Bearer invalid.token.value" \
  "$APP_URL/api/jokes/random")
run_test "OIDC-009" "GET /api/jokes/random with invalid token returns 401" "401" "$STATUS"

# --- OIDC-010: Security headers present in API responses ---
HEADERS=$(curl -s -D - -o /dev/null -H "Authorization: Bearer $USER_TOKEN" "$APP_URL/api/jokes/random")

if echo "$HEADERS" | grep -qi "X-Content-Type-Options: nosniff"; then
  XCTO="present"
else
  XCTO="missing"
fi
run_test "OIDC-010a" "X-Content-Type-Options: nosniff header present" "present" "$XCTO"

if echo "$HEADERS" | grep -qi "X-Frame-Options"; then
  XFO="present"
else
  XFO="missing"
fi
run_test "OIDC-010b" "X-Frame-Options header present" "present" "$XFO"

if echo "$HEADERS" | grep -qi "Cache-Control"; then
  CC="present"
else
  CC="missing"
fi
run_test "OIDC-010c" "Cache-Control header present" "present" "$CC"

# --- OIDC-011: Non-existent path returns 404 ---
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$APP_URL/this/does/not/exist")
# Accept 401, 403, or 404 -- the catch-all deny chain may return any of these
if [ "$STATUS" = "404" ] || [ "$STATUS" = "401" ] || [ "$STATUS" = "403" ]; then
  NOTFOUND_RESULT="$STATUS"
  NOTFOUND_EXPECTED="$STATUS"
else
  NOTFOUND_RESULT="$STATUS"
  NOTFOUND_EXPECTED="404 (or 401/403)"
fi
run_test "OIDC-011" "Non-existent path does not return 200 (got $STATUS)" "$NOTFOUND_EXPECTED" "$NOTFOUND_RESULT"

# --- Summary ---
echo ""
echo "============================================"
echo "  Results: $PASS passed, $FAIL failed, $TOTAL total"
echo "============================================"

if [ "$FAIL" -gt 0 ]; then
  exit 1
fi
