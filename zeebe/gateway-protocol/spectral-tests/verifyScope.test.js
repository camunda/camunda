'use strict';

const { describe, it, before } = require('node:test');
const assert = require('node:assert/strict');
const { lintFixture, filterByRule } = require('./helpers');

const RULE = 'require-scope';
const FIXTURE = 'scope';

describe('verifyScope', () => {
  let violations;

  before(() => {
    const allResults = lintFixture(FIXTURE);
    violations = filterByRule(allResults, RULE);
  });

  // ── Valid cases (should produce zero violations) ─────────────────

  describe('valid: operation with a recognised x-scope', () => {
    it('produces no violations for createThing (physical-tenant)', () => {
      const v = violations.filter((e) => e.message.includes('createThing'));
      assert.equal(v.length, 0);
    });

    it('produces no violations for getThing (cluster-wide)', () => {
      const v = violations.filter(
        (e) => e.message.includes('getThing') && !e.message.includes('Invalid'),
      );
      assert.equal(v.length, 0);
    });
  });

  // ── Invalid cases ────────────────────────────────────────────────

  describe('invalid: operation with missing or unrecognised x-scope', () => {
    it('flags createInvalidThing (missing x-scope)', () => {
      const v = violations.filter((e) => e.message.includes('createInvalidThing'));
      assert.equal(v.length, 1);
    });

    it('flags getInvalidThing (unrecognised x-scope value)', () => {
      const v = violations.filter((e) => e.message.includes('getInvalidThing'));
      assert.equal(v.length, 1);
    });

    it('reports the correct message', () => {
      const v = violations.filter((e) => e.message.includes('createInvalidThing'));
      assert.match(v[0].message, /missing a valid x-scope/);
    });
  });

  // ── Invalid: x-scope disagrees with what the path implies ────────

  describe('invalid: x-scope disagrees with the path-derived scope', () => {
    it('flags createMismatchThing (cluster-wide outside /cluster/v2/)', () => {
      const v = violations.filter((e) => e.message.includes('createMismatchThing'));
      assert.equal(v.length, 1);
      assert.match(v[0].message, /implies x-scope: physical-tenant/);
    });

    it('flags createClusterMismatchThing (physical-tenant under /cluster/v2/)', () => {
      const v = violations.filter((e) => e.message.includes('createClusterMismatchThing'));
      assert.equal(v.length, 1);
      assert.match(v[0].message, /implies x-scope: cluster-wide/);
    });
  });

  it('produces exactly 4 violations total', () => {
    assert.equal(violations.length, 4);
  });
});
