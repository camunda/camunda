'use strict';

const { describe, it, before } = require('node:test');
const assert = require('node:assert/strict');
const { lintFixture, filterByRule } = require('./helpers');

const RULE = 'require-added-in-version';
const FIXTURE = 'added-in-version';

describe('verifyAddedInVersion', () => {
  let violations;

  before(() => {
    const allResults = lintFixture(FIXTURE);
    violations = filterByRule(allResults, RULE);
  });

  // ── Valid cases (should produce zero violations) ─────────────────

  describe('valid: operation with x-added-in-version', () => {
    it('produces no violations for createThing', () => {
      const v = violations.filter(
        (e) => e.message.includes('createThing')
      );
      assert.equal(v.length, 0);
    });

    it('produces no violations for getThing', () => {
      const v = violations.filter(
        (e) => e.message.includes('getThing') && !e.message.includes('Invalid')
      );
      assert.equal(v.length, 0);
    });
  });

  // ── Invalid cases ────────────────────────────────────────────────

  describe('invalid: operation missing x-added-in-version', () => {
    it('flags createInvalidThing', () => {
      const v = violations.filter(
        (e) => e.message.includes('createInvalidThing')
      );
      assert.equal(v.length, 1);
    });

    it('flags getInvalidThing', () => {
      const v = violations.filter(
        (e) => e.message.includes('getInvalidThing')
      );
      assert.equal(v.length, 1);
    });

    it('reports the correct message', () => {
      const v = violations.filter(
        (e) => e.message.includes('createInvalidThing')
      );
      assert.match(v[0].message, /missing x-added-in-version/);
    });

    it('produces exactly 2 violations total', () => {
      assert.equal(violations.length, 2);
    });
  });
});
