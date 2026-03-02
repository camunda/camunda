'use strict';

const { describe, it, before } = require('node:test');
const assert = require('node:assert/strict');
const { lintFixture, filterByRule } = require('./helpers');

const RULE = 'response-properties-must-be-required';
const FIXTURE = 'response-properties-required';

/**
 * Filter violations whose JSON path includes the given API path segment.
 * Error paths are anchored at the operation level (e.g. ['paths', '/valid-flat/get', 'get']),
 * so we match against the API path element (index 1).
 */
function filterByApiPath(violations, apiPathSegment) {
  return violations.filter(
    (v) => Array.isArray(v.path) && v.path[1] === apiPathSegment
  );
}

describe('verifyResponsePropertiesRequired', () => {
  let violations;

  before(() => {
    const allResults = lintFixture(FIXTURE);
    violations = filterByRule(allResults, RULE);
  });

  // ── Valid cases (should produce zero violations) ─────────────────

  describe('valid: flat object with all properties required', () => {
    it('produces no violations', () => {
      const v = filterByApiPath(violations, '/valid-flat/get');
      assert.equal(v.length, 0);
    });
  });

  describe('valid: allOf composition with all properties required', () => {
    it('produces no violations', () => {
      const v = filterByApiPath(violations, '/valid-allof/search');
      assert.equal(v.length, 0);
    });
  });

  describe('valid: allOf with required and properties in separate members', () => {
    it('produces no violations', () => {
      const v = filterByApiPath(violations, '/valid-allof-split/search');
      assert.equal(v.length, 0);
    });
  });

  describe('valid: nested object with all properties required', () => {
    it('produces no violations', () => {
      const v = filterByApiPath(violations, '/valid-nested/get');
      assert.equal(v.length, 0);
    });
  });

  describe('valid: empty schema with no properties', () => {
    it('produces no violations', () => {
      const v = filterByApiPath(violations, '/valid-empty/get');
      assert.equal(v.length, 0);
    });
  });

  // ── Invalid cases ────────────────────────────────────────────────

  describe('invalid: one property not in required', () => {
    it('flags one violation', () => {
      const v = filterByApiPath(violations, '/invalid-missing-one/get');
      assert.equal(v.length, 1);
    });

    it('reports the correct property name', () => {
      const v = filterByApiPath(violations, '/invalid-missing-one/get');
      assert.match(v[0].message, /status/);
      assert.match(v[0].message, /must be listed in `required`/);
    });
  });

  describe('invalid: multiple properties not in required', () => {
    it('flags three violations', () => {
      const v = filterByApiPath(violations, '/invalid-missing-many/get');
      assert.equal(v.length, 3);
    });

    it('reports each missing property', () => {
      const v = filterByApiPath(violations, '/invalid-missing-many/get');
      const messages = v.map((e) => e.message);
      assert.ok(messages.some((m) => /name/.test(m)));
      assert.ok(messages.some((m) => /status/.test(m)));
      assert.ok(messages.some((m) => /count/.test(m)));
    });
  });

  describe('invalid: nested object with non-required property', () => {
    it('flags one violation', () => {
      const v = filterByApiPath(violations, '/invalid-nested/get');
      assert.equal(v.length, 1);
    });

    it('reports the correct property name', () => {
      const v = filterByApiPath(violations, '/invalid-nested/get');
      assert.match(v[0].message, /priority/);
      assert.match(v[0].message, /must be listed in `required`/);
    });
  });

  describe('invalid: allOf composition with missing required entry', () => {
    it('flags one violation', () => {
      const v = filterByApiPath(violations, '/invalid-allof/search');
      assert.equal(v.length, 1);
    });

    it('reports the correct property name', () => {
      const v = filterByApiPath(violations, '/invalid-allof/search');
      assert.match(v[0].message, /items/);
      assert.match(v[0].message, /must be listed in `required`/);
    });
  });

  describe('invalid: array items with non-required property (recursion)', () => {
    it('flags one violation in the item schema', () => {
      const v = filterByApiPath(violations, '/invalid-items-recurse/search');
      assert.equal(v.length, 1);
    });

    it('reports the correct property name', () => {
      const v = filterByApiPath(violations, '/invalid-items-recurse/search');
      assert.match(v[0].message, /category/);
    });
  });

  // ── Total count ──────────────────────────────────────────────────

  it('produces exactly 7 violations across all paths', () => {
    assert.equal(violations.length, 7);
  });
});
