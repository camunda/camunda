'use strict';

const { describe, it, before } = require('node:test');
const assert = require('node:assert/strict');
const { lintFixture, filterByRule, filterByPathSegment } = require('./helpers');

const RULE = 'array-properties-must-be-required';
const FIXTURE = 'array-required';

describe('verifyArrayPropertiesRequired', () => {
  let violations;

  before(() => {
    const allResults = lintFixture(FIXTURE);
    violations = filterByRule(allResults, RULE);
  });

  // ── Valid cases (should produce zero violations) ─────────────────

  describe('valid: required array with allOf composition', () => {
    it('produces no violations', () => {
      const v = filterByPathSegment(violations, 'ValidSearchResult');
      assert.equal(v.length, 0);
    });
  });

  describe('valid: response with no array properties', () => {
    it('produces no violations', () => {
      // ThingResult has no arrays — should never appear in violations.
      // Exclude ThingWithBadArrayResult which also contains the substring.
      const v = violations.filter(
        (e) =>
          e.path.includes('ThingResult') &&
          !e.path.includes('ThingWithBadArrayResult')
      );
      assert.equal(v.length, 0);
    });
  });

  describe('valid: nested object with required array', () => {
    it('produces no violations', () => {
      const v = filterByPathSegment(violations, 'ValidNestedResult');
      assert.equal(v.length, 0);
    });
  });

  describe('valid: allOf with required and properties in separate members', () => {
    it('produces no violations', () => {
      const v = filterByPathSegment(violations, 'AllOfSplitResult');
      assert.equal(v.length, 0);
    });
  });

  // ── Invalid cases ────────────────────────────────────────────────

  describe('invalid: array not listed in required', () => {
    it('flags one violation', () => {
      const v = filterByPathSegment(violations, 'NotRequiredResult');
      assert.equal(v.length, 1);
    });

    it('reports the correct message', () => {
      const v = filterByPathSegment(violations, 'NotRequiredResult');
      assert.match(v[0].message, /items/);
      assert.match(v[0].message, /must be listed in `required`/);
    });
  });

  describe('invalid: array is nullable', () => {
    it('flags one violation', () => {
      const v = filterByPathSegment(violations, 'NullableResult');
      assert.equal(v.length, 1);
    });

    it('reports the correct message', () => {
      const v = filterByPathSegment(violations, 'NullableResult');
      assert.match(v[0].message, /items/);
      assert.match(v[0].message, /must not be `nullable`/);
    });
  });

  describe('invalid: array is both not required and nullable', () => {
    it('flags two violations', () => {
      const v = filterByPathSegment(violations, 'BothResult');
      assert.equal(v.length, 2);
    });

    it('reports both the required and nullable messages', () => {
      const v = filterByPathSegment(violations, 'BothResult');
      const messages = v.map((e) => e.message);
      assert.ok(messages.some((m) => /must be listed in `required`/.test(m)));
      assert.ok(messages.some((m) => /must not be `nullable`/.test(m)));
    });
  });

  describe('invalid: nested object with non-required array', () => {
    it('flags one violation', () => {
      const v = filterByPathSegment(violations, 'InvalidNestedResult');
      assert.equal(v.length, 1);
    });

    it('reports the correct property name', () => {
      const v = filterByPathSegment(violations, 'InvalidNestedResult');
      assert.match(v[0].message, /tags/);
      assert.match(v[0].message, /must be listed in `required`/);
    });
  });

  describe('invalid: array items with non-required nested array (recursion)', () => {
    it('flags one violation in the item schema', () => {
      const v = filterByPathSegment(violations, 'ThingWithBadArrayResult');
      assert.equal(v.length, 1);
    });

    it('reports the correct property name', () => {
      const v = filterByPathSegment(violations, 'ThingWithBadArrayResult');
      assert.match(v[0].message, /labels/);
    });
  });

  // ── Total count ──────────────────────────────────────────────────

  it('produces exactly 6 violations across all paths', () => {
    assert.equal(violations.length, 6);
  });
});
