'use strict';

const { execSync } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');

const GATEWAY_PROTOCOL_DIR = path.resolve(__dirname, '..');
const RULESET_FILE = path.join(GATEWAY_PROTOCOL_DIR, '.spectral.yaml');
const FIXTURES_DIR = path.join(__dirname, 'fixtures');

/**
 * Runs Spectral lint on a fixture's rest-api.yaml using the production ruleset.
 * Returns the parsed JSON results array.
 *
 * If the fixture directory contains a `semantic-kinds.json` file, it is
 * exposed to the verifySemanticKindsRegistered function via the
 * SPECTRAL_SEMANTIC_KINDS_REGISTRY env var so tests can use fixture-local
 * kinds without polluting the production registry.
 */
function lintFixture(fixtureName) {
  return lintFixtureFile(fixtureName, 'rest-api.yaml');
}

/**
 * Runs Spectral lint on a specific file within a fixture directory using the
 * production ruleset. Mirrors the CI *file-level pass* that lints each domain
 * YAML independently (needed for rules whose `given` targets
 * `$.components.schemas[*]` which only matches when linting the file that
 * defines the schemas).
 * Returns the parsed JSON results array.
 */
function lintFixtureFile(fixtureName, fileName) {
  const fixtureDir = path.join(FIXTURES_DIR, fixtureName);
  const specFile = path.join(fixtureDir, fileName);
  const cmd = `spectral lint "${specFile}" --ruleset "${RULESET_FILE}" --format json`;

  const env = { ...process.env };
  const fixtureRegistry = path.join(fixtureDir, 'semantic-kinds.json');
  if (fs.existsSync(fixtureRegistry)) {
    env.SPECTRAL_SEMANTIC_KINDS_REGISTRY = fixtureRegistry;
  }

  try {
    const output = execSync(cmd, {
      encoding: 'utf8',
      cwd: GATEWAY_PROTOCOL_DIR,
      env,
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

module.exports = { lintFixture, lintFixtureFile, filterByRule, filterByPathSegment };
