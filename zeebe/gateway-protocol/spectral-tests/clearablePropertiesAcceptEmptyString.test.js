'use strict';

const { describe, it, before } = require('node:test');
const assert = require('node:assert/strict');
const { lintFixtureFile, filterByRule, filterByPathSegment } = require('./helpers');

const RULE = 'clearable-properties-accept-empty-string';
const FIXTURE = 'clearable-properties';

describe('clearable-properties-accept-empty-string', () => {
  let violations;

  before(() => {
    const allResults = lintFixtureFile(FIXTURE, 'things.yaml');
    violations = filterByRule(allResults, RULE);
  });

  // ── Valid cases (should produce zero violations) ─────────────────

  describe('valid: clear/reset description on a clearable-schema reference', () => {
    it('produces no violations', () => {
      const v = filterByPathSegment(violations, 'ValidClearableReference');
      assert.equal(v.length, 0);
    });
  });

  describe('valid: ordinary date-time property without clear/reset semantics', () => {
    it('produces no violations', () => {
      const v = filterByPathSegment(violations, 'ValidOrdinaryDateTime');
      assert.equal(v.length, 0);
    });
  });

  describe('valid: cleared by an empty string but declares no format', () => {
    it('produces no violations', () => {
      const v = filterByPathSegment(violations, 'ValidClearablePlainString');
      assert.equal(v.length, 0);
    });
  });

  describe('valid: reset by an empty list rather than an empty string', () => {
    it('produces no violations', () => {
      const v = filterByPathSegment(violations, 'ValidClearedByEmptyList');
      assert.equal(v.length, 0);
    });
  });

  describe('valid: the clearable schema itself', () => {
    it('produces no violations', () => {
      const v = filterByPathSegment(violations, 'ClearableDateTime');
      assert.equal(v.length, 0);
    });
  });

  // ── Invalid cases ────────────────────────────────────────────────

  describe('invalid: "Clear ... empty String" with format: date-time', () => {
    it('flags the property', () => {
      const v = filterByPathSegment(violations, 'InvalidClearWithFormat');
      assert.equal(v.length, 1);
      assert.ok(v[0].path.includes('dueDate'));
      assert.match(v[0].message, /must not declare a format/);
    });
  });

  describe('invalid: "Reset ... empty String" with format: date-time', () => {
    it('flags the property', () => {
      const v = filterByPathSegment(violations, 'InvalidResetWithFormat');
      assert.equal(v.length, 1);
      assert.ok(v[0].path.includes('followUpDate'));
    });
  });

  describe('invalid: lowercase "cleared ... empty string" with a non-date format', () => {
    it('flags the property', () => {
      const v = filterByPathSegment(violations, 'InvalidClearedLowercaseOtherFormat');
      assert.equal(v.length, 1);
      assert.ok(v[0].path.includes('callbackUrl'));
    });
  });

  // ── Total count ──────────────────────────────────────────────────

  it('produces exactly 3 violations across all schemas', () => {
    assert.equal(violations.length, 3);
  });
});
