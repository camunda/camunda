'use strict';

const { describe, it, before } = require('node:test');
const assert = require('node:assert/strict');
const { lintFixtureFile, filterByRule, filterByPathSegment } = require('./helpers');

const RULE = 'no-ambiguous-identifier-property';
const RULE_INLINE = 'no-ambiguous-identifier-property-inline';
const FIXTURE = 'ambiguous-identifier';

describe('verifyNoAmbiguousIdentifierProperty', () => {
  let violations;

  before(() => {
    const allResults = lintFixtureFile(FIXTURE, 'things.yaml');
    violations = filterByRule(allResults, RULE);
  });

  // ── Valid cases (should produce zero violations) ─────────────

  describe('valid: schema with qualified property names', () => {
    it('produces no violations for ValidSchema', () => {
      const v = filterByPathSegment(violations, 'ValidSchema');
      assert.equal(v.length, 0);
    });
  });

  describe('valid: allowlisted schema with bare id', () => {
    it('produces no violations for CreateGlobalTaskListenerRequest', () => {
      const v = filterByPathSegment(violations, 'CreateGlobalTaskListenerRequest');
      assert.equal(v.length, 0);
    });
  });

  describe('valid: allowlisted schema with bare name', () => {
    it('produces no violations for CreateClusterVariableRequest', () => {
      const v = filterByPathSegment(violations, 'CreateClusterVariableRequest');
      assert.equal(v.length, 0);
    });
  });

  // ── Invalid cases ────────────────────────────────────────────

  describe('invalid: bare id not in allowlist', () => {
    it('flags BadIdSchema', () => {
      const v = filterByPathSegment(violations, 'BadIdSchema');
      assert.equal(v.length, 1);
      assert.match(v[0].message, /ambiguous property "id"/);
    });
  });

  describe('invalid: bare key not in allowlist', () => {
    it('flags BadKeySchema', () => {
      const v = filterByPathSegment(violations, 'BadKeySchema');
      assert.equal(v.length, 1);
      assert.match(v[0].message, /ambiguous property "key"/);
    });
  });

  describe('invalid: bare name not in allowlist', () => {
    it('flags BadNameSchema', () => {
      const v = filterByPathSegment(violations, 'BadNameSchema');
      assert.equal(v.length, 1);
      assert.match(v[0].message, /ambiguous property "name"/);
    });
  });

  describe('invalid: multiple banned properties on one schema', () => {
    it('flags all three on MultiViolationSchema', () => {
      const v = filterByPathSegment(violations, 'MultiViolationSchema');
      assert.equal(v.length, 3);
    });
  });

  describe('invalid: bare id inside an allOf member', () => {
    it('flags AllOfBadIdSchema', () => {
      const v = filterByPathSegment(violations, 'AllOfBadIdSchema');
      assert.equal(v.length, 1);
      assert.match(v[0].message, /ambiguous property "id"/);
    });

    it('reports the correct path through allOf', () => {
      const v = filterByPathSegment(violations, 'AllOfBadIdSchema');
      assert.ok(v[0].path.includes('allOf'), 'path should include allOf segment');
      assert.ok(v[0].path.includes('properties'), 'path should include properties segment');
      assert.ok(v[0].path.includes('id'), 'path should include the property name');
    });
  });

  describe('total violation count', () => {
    it('produces exactly 7 violations total', () => {
      assert.equal(violations.length, 7);
    });
  });
});

describe('verifyNoAmbiguousIdentifierProperty (inline schemas)', () => {
  let inlineViolations;

  before(() => {
    const allResults = lintFixtureFile(FIXTURE, 'things.yaml');
    inlineViolations = filterByRule(allResults, RULE_INLINE);
  });

  describe('valid: $ref inline schema is not flagged', () => {
    it('produces no violations for /valid-things', () => {
      const v = inlineViolations.filter(r =>
        r.path.some(seg => String(seg).includes('/valid-things'))
      );
      assert.equal(v.length, 0);
    });
  });

  describe('invalid: truly inline schema with bare name', () => {
    it('flags the inline schema under /bad-inline-things', () => {
      const v = inlineViolations.filter(r =>
        r.path.some(seg => String(seg).includes('/bad-inline-things'))
      );
      assert.equal(v.length, 1);
      assert.match(v[0].message, /ambiguous property "name"/);
    });

    it('includes "(inline schema)" in the label', () => {
      const v = inlineViolations.filter(r =>
        r.path.some(seg => String(seg).includes('/bad-inline-things'))
      );
      assert.match(v[0].message, /\(inline schema\)/);
    });
  });

  describe('total inline violation count', () => {
    it('produces exactly 1 inline violation total', () => {
      assert.equal(inlineViolations.length, 1);
    });
  });
});
