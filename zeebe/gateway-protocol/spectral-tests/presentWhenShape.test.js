'use strict';

const { describe, it, before } = require('node:test');
const assert = require('node:assert/strict');
const { lintFixture, filterByRule, filterByPathSegment } = require('./helpers');

const FIXTURE = 'present-when';
const SHAPE_RULE = 'present-when-shape';

describe('present-when-shape', () => {
  let shape;

  before(() => {
    const allResults = lintFixture(FIXTURE);
    shape = filterByRule(allResults, SHAPE_RULE);
  });

  const shapeFor = (schemaName) => filterByPathSegment(shape, schemaName);

  // ── Valid markers (no shape violations) ──────────────────────────

  describe('valid markers', () => {
    it('accepts a boolean equals literal', () => {
      assert.equal(shapeFor('ValidBooleanTrueResult').length, 0);
    });

    it('accepts a string equals literal', () => {
      assert.equal(shapeFor('ValidStringEqualsResult').length, 0);
    });

    it('accepts a number equals literal', () => {
      assert.equal(shapeFor('ValidNumberEqualsResult').length, 0);
    });
  });

  // ── Invalid markers (exactly one shape violation each) ───────────

  describe('invalid markers', () => {
    it('flags a marker missing the equals literal', () => {
      const v = shapeFor('InvalidMissingEqualsResult');
      assert.equal(v.length, 1);
      assert.match(v[0].message, /equals/);
    });

    it('flags a marker missing the request field', () => {
      const v = shapeFor('InvalidMissingRequestResult');
      assert.equal(v.length, 1);
      assert.match(v[0].message, /request/);
    });

    it('flags an empty request field', () => {
      assert.equal(shapeFor('InvalidEmptyRequestResult').length, 1);
    });

    it('flags an unknown extra property', () => {
      assert.equal(shapeFor('InvalidExtraPropertyResult').length, 1);
    });

    it('flags a non-scalar (array) equals value', () => {
      assert.equal(shapeFor('InvalidArrayEqualsResult').length, 1);
    });

    it('flags a non-scalar (object) equals value', () => {
      assert.equal(shapeFor('InvalidObjectEqualsResult').length, 1);
    });
  });
});
