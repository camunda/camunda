'use strict';

const { describe, it, before } = require('node:test');
const assert = require('node:assert/strict');
const { lintFixtureFile, filterByRule, filterByPathSegment } = require('./helpers');

const RULE = 'no-redundant-required';
const FIXTURE = 'no-redundant-required';

describe('verifyNoRedundantRequired', () => {
  let violations;

  before(() => {
    const allResults = lintFixtureFile(FIXTURE, 'things.yaml');
    violations = filterByRule(allResults, RULE);
  });

  // ── Valid cases (should produce zero violations) ─────────────────

  describe('valid: schema with no allOf', () => {
    it('produces no violations', () => {
      const v = filterByPathSegment(violations, 'ValidNoAllOf');
      assert.equal(v.length, 0);
    });
  });

  describe('valid: allOf present but no overlap with own required', () => {
    it('produces no violations', () => {
      const v = filterByPathSegment(violations, 'ValidNoOverlap');
      assert.equal(v.length, 0);
    });
  });

  // ── Invalid cases ────────────────────────────────────────────────

  describe('invalid: own required duplicates a direct allOf member', () => {
    it('flags one violation', () => {
      const v = filterByPathSegment(violations, 'InvalidDirectDuplicate');
      assert.equal(v.length, 1);
    });

    it('reports the duplicated field name', () => {
      const v = filterByPathSegment(violations, 'InvalidDirectDuplicate');
      assert.match(v[0].message, /`id`/);
      assert.match(v[0].message, /already required via an allOf-composed schema/);
    });
  });

  describe('invalid: multiple redundant entries against separate allOf members', () => {
    it('flags two violations', () => {
      const v = filterByPathSegment(violations, 'InvalidMultipleDuplicates');
      assert.equal(v.length, 2);
    });

    it('reports both duplicated field names', () => {
      const v = filterByPathSegment(violations, 'InvalidMultipleDuplicates');
      const messages = v.map((e) => e.message);
      assert.ok(messages.some((m) => /`id`/.test(m)));
      assert.ok(messages.some((m) => /`otherId`/.test(m)));
    });
  });

  describe('invalid: redundant against a transitive (allOf-of-allOf) parent', () => {
    it('flags one violation', () => {
      const v = filterByPathSegment(violations, 'InvalidTransitiveDuplicate');
      assert.equal(v.length, 1);
    });

    it('reports the duplicated field name', () => {
      const v = filterByPathSegment(violations, 'InvalidTransitiveDuplicate');
      assert.match(v[0].message, /`id`/);
    });
  });

  // ── Total count ──────────────────────────────────────────────────

  it('produces exactly 4 violations across all schemas', () => {
    assert.equal(violations.length, 4);
  });
});
