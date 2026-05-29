'use strict';

const { describe, it, before } = require('node:test');
const assert = require('node:assert/strict');
const { lintFixture, filterByRule } = require('./helpers');

const RULE = 'require-security-declared';
const FIXTURE = 'security-declared';

describe('verifySecurityDeclared', () => {
  let violations;

  before(() => {
    const allResults = lintFixture(FIXTURE);
    violations = filterByRule(allResults, RULE);
  });

  const violationsFor = (operationId) =>
    violations.filter((v) => v.message.includes(`"${operationId}"`));

  // ── Valid cases (should produce zero violations) ─────────────────

  describe('valid: operation declares accepted security shapes', () => {
    it('produces no violations for both schemes (BearerAuth + basicAuth)', () => {
      assert.equal(violationsFor('getValidBoth').length, 0);
    });

    it('produces no violations for BearerAuth only', () => {
      assert.equal(violationsFor('getValidBearerOnly').length, 0);
    });

    it('produces no violations for basicAuth only', () => {
      assert.equal(violationsFor('getValidBasicOnly').length, 0);
    });

    it('produces no violations for explicit public (security: [])', () => {
      assert.equal(violationsFor('getValidPublic').length, 0);
    });
  });

  // ── Invalid cases ────────────────────────────────────────────────

  describe('invalid: missing security block', () => {
    it('flags operation with no security field', () => {
      const v = violationsFor('getInvalidMissingSecurity');
      assert.equal(v.length, 1);
      assert.match(v[0].message, /missing `security:`/);
    });
  });

  describe('invalid: unknown scheme reference', () => {
    it('flags operation referencing scheme other than BearerAuth/basicAuth', () => {
      const v = violationsFor('getInvalidUnknownScheme');
      assert.equal(v.length, 1);
      assert.match(v[0].message, /unknown scheme `OAuth2`/);
    });
  });

  describe('invalid: non-empty scopes', () => {
    it('flags BearerAuth/basicAuth entry with scope values', () => {
      const v = violationsFor('getInvalidNonEmptyScopes');
      assert.equal(v.length, 1);
      assert.match(v[0].message, /empty scopes array/);
    });
  });

  describe('invalid: empty entry', () => {
    it('flags an entry with no scheme name (- {})', () => {
      const v = violationsFor('getInvalidEmptyEntry');
      assert.equal(v.length, 1);
      assert.match(v[0].message, /empty object/);
    });
  });

  describe('invalid: multiple schemes ANDed in one entry', () => {
    it('flags an entry containing more than one scheme', () => {
      const v = violationsFor('getInvalidMultiSchemeEntry');
      assert.equal(v.length, 1);
      assert.match(v[0].message, /multiple schemes/);
    });
  });

  describe('invalid: duplicate scheme entries', () => {
    it('flags a scheme that appears more than once', () => {
      const v = violationsFor('getInvalidDuplicateScheme');
      assert.equal(v.length, 1);
      assert.match(v[0].message, /more than once/);
    });
  });

  describe('invalid: security is not an array', () => {
    it('flags an object value for security', () => {
      const v = violationsFor('getInvalidNotAnArray');
      assert.equal(v.length, 1);
      assert.match(v[0].message, /must be an array/);
    });
  });

  describe('invalid: scopes value is not an array', () => {
    it('flags a non-array scopes value', () => {
      const v = violationsFor('getInvalidScopesNotAnArray');
      assert.equal(v.length, 1);
      assert.match(v[0].message, /must be an array \(scopes\)/);
    });
  });

  describe('total count', () => {
    it('produces exactly one violation per invalid operation (8 total)', () => {
      assert.equal(violations.length, 8);
    });
  });
});
