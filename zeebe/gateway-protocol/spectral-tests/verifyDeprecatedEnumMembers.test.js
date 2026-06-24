'use strict';

const { describe, it, before } = require('node:test');
const assert = require('node:assert/strict');
const { lintFixtureFile, filterByRule, filterByPathSegment } = require('./helpers');

const RULE = 'valid-deprecated-enum-members';
const FIXTURE = 'deprecated-enum-members';

describe('verifyDeprecatedEnumMembers', () => {
  let violations;

  before(() => {
    // Lint the domain file directly (mirrors the CI file-level pass that lints
    // each *.yaml independently — required because `$.components.schemas[*]`
    // only matches schemas defined in the file being linted).
    const allResults = lintFixtureFile(FIXTURE, 'enums.yaml');
    violations = filterByRule(allResults, RULE);
  });

  // ── Valid cases (should produce zero violations) ─────────────────

  describe('valid: enum without x-deprecated-enum-members', () => {
    it('produces no violations', () => {
      const v = filterByPathSegment(violations, 'ValidNoExtensionEnum');
      assert.equal(v.length, 0);
    });
  });

  describe('valid: enum with single deprecated member', () => {
    it('produces no violations', () => {
      const v = filterByPathSegment(violations, 'ValidWithExtensionEnum');
      assert.equal(v.length, 0);
    });
  });

  describe('valid: enum with multiple deprecated members', () => {
    it('produces no violations', () => {
      const v = filterByPathSegment(violations, 'ValidMultipleDeprecatedEnum');
      assert.equal(v.length, 0);
    });
  });

  // ── Invalid cases ────────────────────────────────────────────────

  describe('invalid: x-deprecated-enum-members is not an array', () => {
    it('flags one violation', () => {
      const v = filterByPathSegment(violations, 'InvalidNotArrayEnum');
      assert.equal(v.length, 1);
    });

    it('reports the correct message', () => {
      const v = filterByPathSegment(violations, 'InvalidNotArrayEnum');
      assert.match(v[0].message, /must be an array/);
    });
  });

  describe('invalid: x-deprecated-enum-members is an empty array', () => {
    it('flags one violation', () => {
      const v = filterByPathSegment(violations, 'InvalidEmptyArrayEnum');
      assert.equal(v.length, 1);
    });

    it('reports the correct message', () => {
      const v = filterByPathSegment(violations, 'InvalidEmptyArrayEnum');
      assert.match(v[0].message, /must not be empty/);
    });
  });

  describe('invalid: entry missing name', () => {
    it('flags one violation', () => {
      const v = filterByPathSegment(violations, 'InvalidMissingNameEnum');
      assert.equal(v.length, 1);
    });

    it('reports the correct message', () => {
      const v = filterByPathSegment(violations, 'InvalidMissingNameEnum');
      assert.match(v[0].message, /\.name.*must be a non-empty string/);
    });
  });

  describe('invalid: entry missing deprecatedInVersion', () => {
    it('flags one violation', () => {
      const v = filterByPathSegment(violations, 'InvalidMissingVersionEnum');
      assert.equal(v.length, 1);
    });

    it('reports the correct message', () => {
      const v = filterByPathSegment(violations, 'InvalidMissingVersionEnum');
      assert.match(v[0].message, /deprecatedInVersion.*must be a semver string/);
    });
  });

  describe('invalid: deprecatedInVersion is not valid semver', () => {
    it('flags one violation', () => {
      const v = filterByPathSegment(violations, 'InvalidBadSemverEnum');
      assert.equal(v.length, 1);
    });

    it('reports the correct message', () => {
      const v = filterByPathSegment(violations, 'InvalidBadSemverEnum');
      assert.match(v[0].message, /deprecatedInVersion.*must be a semver string/);
    });
  });

  describe('invalid: deprecated name does not exist in enum', () => {
    it('flags one violation', () => {
      const v = filterByPathSegment(violations, 'InvalidNameNotInEnumEnum');
      assert.equal(v.length, 1);
    });

    it('reports the correct message', () => {
      const v = filterByPathSegment(violations, 'InvalidNameNotInEnumEnum');
      assert.match(v[0].message, /NONEXISTENT.*is not listed in `enum`/);
    });
  });

  describe('invalid: duplicate name entries', () => {
    it('flags one violation', () => {
      const v = filterByPathSegment(violations, 'InvalidDuplicateNameEnum');
      assert.equal(v.length, 1);
    });

    it('reports the correct message', () => {
      const v = filterByPathSegment(violations, 'InvalidDuplicateNameEnum');
      assert.match(v[0].message, /"A".*is duplicated/);
    });
  });

  describe('invalid: entry with unexpected extra keys', () => {
    it('flags one violation', () => {
      const v = filterByPathSegment(violations, 'InvalidExtraKeysEnum');
      assert.equal(v.length, 1);
    });

    it('reports the correct message', () => {
      const v = filterByPathSegment(violations, 'InvalidExtraKeysEnum');
      assert.match(v[0].message, /unexpected keys.*reason/);
    });
  });

  describe('invalid: extension present but enum missing', () => {
    it('flags one violation', () => {
      const v = filterByPathSegment(violations, 'InvalidNoEnumEnum');
      assert.equal(v.length, 1);
    });

    it('reports the correct message', () => {
      const v = filterByPathSegment(violations, 'InvalidNoEnumEnum');
      assert.match(v[0].message, /requires the schema to have a non-empty `enum` array/);
    });
  });
});
