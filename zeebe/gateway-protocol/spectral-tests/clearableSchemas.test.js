'use strict';

const { describe, it, before } = require('node:test');
const assert = require('node:assert/strict');
const { lintFixtureFile, filterByRule, filterByPathSegment } = require('./helpers');

const SCHEMA_RULE = 'clearable-schema-accepts-empty-string';
const REFERENCE_RULE = 'clearable-reference-declares-no-format';
const FIXTURE = 'clearable';

describe('clearable schemas (x-clearable)', () => {
  let schemaViolations;
  let referenceViolations;

  before(() => {
    const allResults = lintFixtureFile(FIXTURE, 'things.yaml');
    schemaViolations = filterByRule(allResults, SCHEMA_RULE);
    referenceViolations = filterByRule(allResults, REFERENCE_RULE);
  });

  describe(SCHEMA_RULE, () => {
    // ── Valid cases ──────────────────────────────────────────────────

    describe('valid: well-formed clearable schema', () => {
      it('produces no violations', () => {
        const v = filterByPathSegment(schemaViolations, 'ValidClearableDateTime');
        assert.equal(v.length, 0);
      });
    });

    // ── Invalid cases ────────────────────────────────────────────────

    describe('invalid: x-clearable is not the boolean true', () => {
      it('flags the schema', () => {
        const v = filterByPathSegment(schemaViolations, 'InvalidClearableFlagNotTrue');
        assert.equal(v.length, 1);
      });
    });

    describe('invalid: no anyOf member accepts the empty string', () => {
      it('flags the schema', () => {
        const v = filterByPathSegment(schemaViolations, 'InvalidClearableWithoutEmptyBranch');
        assert.equal(v.length, 1);
        assert.match(v[0].message, /maxLength: 0/);
      });
    });

    describe('invalid: clearable schema declares its own format', () => {
      it('flags the schema', () => {
        const v = filterByPathSegment(schemaViolations, 'InvalidClearableWithOwnFormat');
        assert.equal(v.length, 1);
      });
    });

    it('produces exactly 3 violations', () => {
      assert.equal(schemaViolations.length, 3);
    });
  });

  describe(REFERENCE_RULE, () => {
    // ── Valid cases ──────────────────────────────────────────────────

    describe('valid: property referencing a clearable schema', () => {
      it('produces no violations', () => {
        const v = filterByPathSegment(referenceViolations, 'ValidClearableReference');
        assert.equal(v.length, 0);
      });
    });

    describe('valid: ordinary date-time property', () => {
      it('produces no violations', () => {
        const v = filterByPathSegment(referenceViolations, 'ValidOrdinaryDateTime');
        assert.equal(v.length, 0);
      });
    });

    describe('valid: format next to a reference to a non-clearable schema', () => {
      it('produces no violations', () => {
        const v = filterByPathSegment(referenceViolations, 'ValidFormatNextToOrdinaryReference');
        assert.equal(v.length, 0);
      });
    });

    // ── Invalid cases ────────────────────────────────────────────────

    describe('invalid: format next to a clearable reference', () => {
      it('flags the property', () => {
        const v = filterByPathSegment(referenceViolations, 'InvalidFormatNextToClearableReference');
        assert.equal(v.length, 1);
        assert.ok(v[0].path.includes('followUpDate'));
        assert.match(v[0].message, /must not declare a format/);
      });
    });

    it('produces exactly 1 violation', () => {
      assert.equal(referenceViolations.length, 1);
    });
  });
});
