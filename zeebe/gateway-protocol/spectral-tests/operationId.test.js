'use strict';

const { describe, it, before } = require('node:test');
const assert = require('node:assert/strict');
const { lintFixture, filterByRule, filterByPathSegment } = require('./helpers');

const RULE = 'operation-operationId';
const FIXTURE = 'operation-id';
// Spectral severity codes: 0 = error, 1 = warn.
const ERROR = 0;

describe('operation-operationId', () => {
  let violations;

  before(() => {
    violations = filterByRule(lintFixture(FIXTURE), RULE);
  });

  it('produces no violation for an operation with an operationId', () => {
    assert.equal(filterByPathSegment(violations, '/valid/thing').length, 0);
  });

  it('flags an operation without an operationId as an error', () => {
    const v = filterByPathSegment(violations, '/invalid/thing');
    assert.equal(v.length, 1);
    assert.equal(v[0].severity, ERROR);
  });

  it('produces exactly 1 violation total', () => {
    assert.equal(violations.length, 1);
  });
});
