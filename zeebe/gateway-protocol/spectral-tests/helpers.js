'use strict';

const { execSync } = require('node:child_process');
const path = require('node:path');

const GATEWAY_PROTOCOL_DIR = path.resolve(__dirname, '..');
const RULESET_FILE = path.join(GATEWAY_PROTOCOL_DIR, '.spectral.yaml');
const FIXTURES_DIR = path.join(__dirname, 'fixtures');

/**
 * Runs Spectral lint on a fixture's rest-api.yaml using the production ruleset.
 * Returns the parsed JSON results array.
 */
function lintFixture(fixtureName) {
  const specFile = path.join(FIXTURES_DIR, fixtureName, 'rest-api.yaml');
  const cmd = `spectral lint "${specFile}" --ruleset "${RULESET_FILE}" --format json`;

  try {
    const output = execSync(cmd, {
      encoding: 'utf8',
      cwd: GATEWAY_PROTOCOL_DIR,
    });
    return output.trim() ? JSON.parse(output) : [];
  } catch (err) {
    // Spectral exits non-zero when violations are found — stdout still has JSON.
    if (err.stdout) {
      return JSON.parse(err.stdout);
    }
    throw err;
  }
}

/**
 * Filters Spectral results to only violations for a specific rule code.
 */
function filterByRule(results, ruleName) {
  return results.filter((r) => r.code === ruleName);
}

/**
 * Filters violations to those whose JSON path contains the given segment.
 * Works with both path-level violations (path[1] = API path) and
 * schema-level violations (path[2] = schema name) since Spectral remaps
 * error locations back to the source file where schemas are defined.
 */
function filterByPathSegment(violations, segment) {
  return violations.filter(
    (v) => Array.isArray(v.path) && v.path.includes(segment)
  );
}

module.exports = { lintFixture, filterByRule, filterByPathSegment };
