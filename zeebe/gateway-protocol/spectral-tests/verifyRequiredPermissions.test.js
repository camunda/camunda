'use strict';

const { describe, it, before } = require('node:test');
const assert = require('node:assert/strict');
const { lintFixture, filterByRule, filterByPathSegment } = require('./helpers');

const FIXTURE = 'required-permissions';
const VERIFY_RULE = 'verify-required-permissions';
const SHAPE_RULE = 'required-permissions-shape';

describe('verifyRequiredPermissions', () => {
  let verify;
  let shape;

  before(() => {
    const allResults = lintFixture(FIXTURE);
    verify = filterByRule(allResults, VERIFY_RULE);
    shape = filterByRule(allResults, SHAPE_RULE);
  });

  const verifyFor = (operationId) =>
    verify.filter((e) => e.message.includes(`"${operationId}"`));

  // ── Valid cases (no violations from either rule) ─────────────────

  describe('valid declarations', () => {
    it('accepts a static {resourceType, permissionType} pair', () => {
      assert.equal(verifyFor('validStatic').length, 0);
      assert.equal(filterByPathSegment(shape, '/valid/static').length, 0);
    });

    it('accepts an anyOf OR-group of valid pairs', () => {
      assert.equal(verifyFor('validAnyOf').length, 0);
      assert.equal(filterByPathSegment(shape, '/valid/any-of').length, 0);
    });

    it('accepts a dynamic entry with a note', () => {
      assert.equal(verifyFor('validDynamic').length, 0);
      assert.equal(filterByPathSegment(shape, '/valid/dynamic').length, 0);
    });

    it('accepts an empty array (explicitly public)', () => {
      assert.equal(verifyFor('validPublic').length, 0);
      assert.equal(filterByPathSegment(shape, '/valid/public').length, 0);
    });
  });

  // ── Completeness / registry-validity (verify rule) ───────────────

  describe('completeness gap guard', () => {
    it('flags an operation missing x-required-permissions', () => {
      const v = verifyFor('invalidMissing');
      assert.equal(v.length, 1);
      assert.match(v[0].message, /missing x-required-permissions/);
    });
  });

  describe('registry validity', () => {
    it('flags an unknown resourceType', () => {
      const v = verifyFor('invalidUnknownResource');
      assert.equal(v.length, 1);
      assert.match(v[0].message, /unknown resourceType 'WIDGET'/);
    });

    it('flags a permissionType not supported by the resource', () => {
      const v = verifyFor('invalidUnsupportedPermission');
      assert.equal(v.length, 1);
      assert.match(v[0].message, /permissionType 'DELETE' which is not supported/);
    });

    it('flags an invalid pair inside an anyOf branch', () => {
      const v = verifyFor('invalidAnyOfBranch');
      assert.equal(v.length, 1);
      assert.match(v[0].message, /permissionType 'FLY' which is not supported/);
    });

    it('produces exactly 4 verify violations total', () => {
      assert.equal(verify.length, 4);
    });
  });

  // ── Schema shape (shape rule) ────────────────────────────────────

  describe('entry shape', () => {
    it('flags a dynamic entry missing its note', () => {
      assert.equal(filterByPathSegment(shape, '/invalid/dynamic-no-note').length, 1);
    });

    it('flags an entry with an unknown extra property', () => {
      assert.equal(filterByPathSegment(shape, '/invalid/extra-property').length, 1);
    });

    it('flags an entry combining a static pair and anyOf', () => {
      assert.equal(filterByPathSegment(shape, '/invalid/mixed-forms').length, 1);
    });

    it('produces exactly 3 shape violations total', () => {
      assert.equal(shape.length, 3);
    });
  });
});
