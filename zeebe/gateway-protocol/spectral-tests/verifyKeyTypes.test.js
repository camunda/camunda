'use strict';

const { describe, it, before } = require('node:test');
const assert = require('node:assert/strict');
const { lintFixtureFile, filterByRule, filterByPathSegment } = require('./helpers');

const FIXTURE = 'key-properties';

describe('verifyKeyTypesPaths', () => {
  const RULE = 'operation-key-properties-must-be-strings';
  let violations;

  before(() => {
    violations = filterByRule(lintFixtureFile(FIXTURE, 'things.yaml'), RULE);
  });

  describe('valid: string key path parameter', () => {
    it('produces no violations', () => {
      assert.equal(filterByPathSegment(violations, '/valid/{thingKey}').length, 0);
    });
  });

  describe('valid: non-key path parameter of type integer', () => {
    it('produces no violations', () => {
      assert.equal(filterByPathSegment(violations, '/valid/{thingId}/count').length, 0);
    });
  });

  describe('invalid: integer key path parameter', () => {
    it('flags one error at the parameter schema type', () => {
      const v = filterByPathSegment(violations, '/invalid/{otherKey}');
      assert.equal(v.length, 1);
      assert.equal(v[0].severity, 0);
      assert.deepEqual(v[0].path.slice(-2), ['schema', 'type']);
    });
  });

  it('flags only the invalid parameter', () => {
    assert.equal(violations.length, 1);
  });
});

describe('verifyKeyTypesSchemas', () => {
  const RULE = 'schema-key-properties-must-be-strings';
  let violations;

  before(() => {
    violations = filterByRule(lintFixtureFile(FIXTURE, 'things.yaml'), RULE);
  });

  describe('valid: string key property', () => {
    it('produces no violations', () => {
      assert.equal(filterByPathSegment(violations, 'ValidStringKey').length, 0);
    });
  });

  describe('valid: key property composing BasicStringFilterProperty', () => {
    it('produces no violations', () => {
      assert.equal(filterByPathSegment(violations, 'ValidFilterKey').length, 0);
    });
  });

  describe('valid: non-key property of type integer', () => {
    it('produces no violations', () => {
      assert.equal(filterByPathSegment(violations, 'ValidNonKeyInteger').length, 0);
    });
  });

  describe('known gap: key property composing an integer via allOf', () => {
    // Any allOf passes. Update this test if the check is tightened.
    it('produces no violations', () => {
      assert.equal(filterByPathSegment(violations, 'AllOfIntegerKeyPasses').length, 0);
    });
  });

  describe('invalid: integer key property', () => {
    it('flags one error at the property type', () => {
      const v = filterByPathSegment(violations, 'InvalidIntegerKey');
      assert.equal(v.length, 1);
      assert.equal(v[0].severity, 0);
      assert.deepEqual(v[0].path.slice(-2), ['thingKey', 'type']);
    });
  });

  describe('invalid: several non-string key properties in one schema', () => {
    it('flags each key property and ignores the non-key one', () => {
      const v = filterByPathSegment(violations, 'InvalidMultipleKeys');
      const flagged = v.map((e) => e.path.at(-2)).sort();
      assert.deepEqual(flagged, ['firstKey', 'secondKey']);
    });
  });

  it('flags only the invalid properties', () => {
    assert.equal(violations.length, 3);
  });
});
