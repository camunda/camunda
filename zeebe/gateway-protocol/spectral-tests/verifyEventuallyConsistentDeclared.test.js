'use strict';

const { describe, it, before } = require('node:test');
const assert = require('node:assert/strict');
const { lintFixture, filterByRule, filterByPathSegment } = require('./helpers');

const RULE = 'require-eventually-consistent';
const COMMANDS_RULE = 'no-eventually-consistent-on-commands';
const FIXTURE = 'eventually-consistent';

describe('verifyEventuallyConsistentDeclared', () => {
  let allResults;
  let violations;

  before(() => {
    allResults = lintFixture(FIXTURE);
    violations = filterByRule(allResults, RULE);
  });

  // ── Valid cases (should produce zero violations) ─────────────────

  describe('valid: operation declaring a boolean x-eventually-consistent', () => {
    it('produces no violations for getThing (true)', () => {
      const v = violations.filter((e) => e.message.includes('"getThing"'));
      assert.equal(v.length, 0);
    });

    it('produces no violations for createThing (false)', () => {
      const v = violations.filter((e) => e.message.includes('"createThing"'));
      assert.equal(v.length, 0);
    });

    it('does not treat path-level parameters or servers as operations', () => {
      const v = violations.filter((e) => e.message.includes('getPathLevelThing'));
      assert.equal(v.length, 0);
      assert.equal(filterByPathSegment(violations, 'parameters').length, 0);
      assert.equal(filterByPathSegment(violations, 'servers').length, 0);
    });
  });

  // ── Invalid cases ────────────────────────────────────────────────

  describe('invalid: operation without a boolean x-eventually-consistent', () => {
    it('flags createMissingThing (missing)', () => {
      const v = violations.filter((e) => e.message.includes('createMissingThing'));
      assert.equal(v.length, 1);
    });

    it('flags createStringThing (quoted string)', () => {
      const v = violations.filter((e) => e.message.includes('createStringThing'));
      assert.equal(v.length, 1);
    });

    it('flags createNullThing (null)', () => {
      const v = violations.filter((e) => e.message.includes('createNullThing'));
      assert.equal(v.length, 1);
    });

    it('reports the correct message', () => {
      const v = violations.filter((e) => e.message.includes('createMissingThing'));
      assert.match(v[0].message, /is missing a valid x-eventually-consistent/);
      assert.match(v[0].message, /POST \/invalid\/missing/);
    });

    it('produces exactly 3 violations total', () => {
      assert.equal(violations.length, 3);
    });
  });

  describe('coexistence with no-eventually-consistent-on-commands', () => {
    it('does not crash the commands rule and flags nothing on valid commands', () => {
      const v = filterByRule(allResults, COMMANDS_RULE);
      assert.equal(v.filter((e) => e.message.includes('createThing')).length, 0);
    });
  });
});
