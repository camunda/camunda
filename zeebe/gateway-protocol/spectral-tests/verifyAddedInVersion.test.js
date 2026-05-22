'use strict';

const { describe, it, before } = require('node:test');
const assert = require('node:assert/strict');
const { lintFixture, filterByRule, filterByPathSegment } = require('./helpers');

const RULE = 'require-added-in-version';
const FIXTURE = 'added-in-version';

const PROPS_RULE = 'properties-added-in-version-shape';
const PROPS_FIXTURE = 'properties-added-in-version';

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

describe('properties-added-in-version-shape', () => {
  let violations;

  before(() => {
    const allResults = lintFixture(PROPS_FIXTURE);
    violations = filterByRule(allResults, PROPS_RULE);
  });

  // ── Valid cases (should produce zero violations) ─────────────────

  describe('valid shapes', () => {
    it('accepts a single well-formed entry', () => {
      assert.equal(
        filterByPathSegment(violations, 'ValidPropsAddedInVersion').length,
        0,
      );
    });

    it('accepts multiple entries including patch-level versions', () => {
      assert.equal(
        filterByPathSegment(violations, 'ValidPropsAddedInVersionMultiple').length,
        0,
      );
    });

    it('accepts filter operator names (e.g. "$eq") as propertyName', () => {
      assert.equal(
        filterByPathSegment(violations, 'ValidPropsAddedInVersionDollarOperator').length,
        0,
      );
    });
  });

  // ── Invalid cases ────────────────────────────────────────────────

  describe('invalid shapes', () => {
    it('rejects an object value instead of an array', () => {
      assert.equal(
        filterByPathSegment(violations, 'InvalidPropsAddedInVersionNotArray').length,
        1,
      );
    });

    it('rejects an empty array', () => {
      assert.equal(
        filterByPathSegment(violations, 'InvalidPropsAddedInVersionEmptyArray').length,
        1,
      );
    });

    it('rejects an entry missing propertyName', () => {
      assert.equal(
        filterByPathSegment(violations, 'InvalidPropsAddedInVersionMissingPropertyName').length,
        1,
      );
    });

    it('rejects an entry missing addedInVersion', () => {
      assert.equal(
        filterByPathSegment(violations, 'InvalidPropsAddedInVersionMissingAddedInVersion').length,
        1,
      );
    });

    it('rejects an entry with an unrecognised extra key', () => {
      assert.equal(
        filterByPathSegment(violations, 'InvalidPropsAddedInVersionExtraKey').length,
        1,
      );
    });

    it('rejects addedInVersion that is not in semver-ish form', () => {
      assert.equal(
        filterByPathSegment(violations, 'InvalidPropsAddedInVersionBadVersionFormat').length,
        1,
      );
    });

    it('rejects an empty propertyName', () => {
      assert.equal(
        filterByPathSegment(violations, 'InvalidPropsAddedInVersionEmptyPropertyName').length,
        1,
      );
    });

    it('produces exactly one violation per invalid schema (7 total)', () => {
      assert.equal(violations.length, 7);
    });
  });
});
