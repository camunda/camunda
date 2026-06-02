'use strict';

const { before, describe, it } = require('node:test');
const assert = require('node:assert/strict');
const { lintFixture, filterByRule } = require('./helpers');

const RULE = 'require-operation-security-modes';

describe('verifyOperationSecurityModes', () => {
  let violations;

  before(() => {
    const allResults = lintFixture('operation-security-modes');
    violations = filterByRule(allResults, RULE);
  });

  describe('valid operation security declarations', () => {
    it('accepts inherited default security', () => {
      assert.equal(
        violations.filter((v) => v.message.includes('validInheritedSecurity')).length,
        0,
      );
    });

    it('accepts explicit basicAuth + BearerAuth', () => {
      assert.equal(
        violations.filter((v) => v.message.includes('validExplicitSecurity')).length,
        0,
      );
    });

    it('accepts explicit empty security', () => {
      assert.equal(
        violations.filter((v) => v.message.includes('validPublicSecurity')).length,
        0,
      );
    });
  });

  describe('invalid operation security declarations', () => {
    it('rejects bearer-only declarations', () => {
      assert.equal(
        violations.filter((v) => v.message.includes('invalidBearerOnlySecurity')).length,
        1,
      );
    });

    it('rejects unexpected schemes', () => {
      assert.equal(
        violations.filter((v) => v.message.includes('invalidCustomSchemeSecurity')).length,
        1,
      );
    });

    it('rejects non-empty scopes', () => {
      assert.equal(
        violations.filter((v) => v.message.includes('invalidNonEmptyScopesSecurity')).length,
        1,
      );
    });

    it('produces exactly 3 violations', () => {
      assert.equal(violations.length, 3);
    });
  });
});

describe('verifyOperationSecurityModes without global defaults', () => {
  let violations;

  before(() => {
    const allResults = lintFixture('operation-security-modes-no-default');
    violations = filterByRule(allResults, RULE);
  });

  it('rejects operations without effective security declaration', () => {
    assert.equal(
      violations.filter((v) => v.message.includes('invalidMissingSecurity')).length,
      1,
    );
  });

  it('keeps explicit declarations valid even without global defaults', () => {
    assert.equal(
      violations.filter((v) => v.message.includes('validExplicitSecurityNoDefault')).length,
      0,
    );
    assert.equal(
      violations.filter((v) => v.message.includes('validPublicSecurityNoDefault')).length,
      0,
    );
  });

  it('produces exactly 1 violation', () => {
    assert.equal(violations.length, 1);
  });
});
