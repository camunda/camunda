'use strict';

const { describe, it, before } = require('node:test');
const assert = require('node:assert/strict');
const {
  lintFixture,
  lintFixtureFile,
  filterByRule,
  filterByPathSegment,
} = require('./helpers');

const RULE = 'operation-operationId';
const FIXTURE = 'operation-id';
// Spectral severity codes: 0 = error, 1 = warn.
const ERROR = 0;

// CI lints the spec twice: once from rest-api.yaml (resolving $refs) and once
// per file. The rule must fail the build in both passes.
const PASSES = [
  ['structural pass', () => lintFixture(FIXTURE)],
  ['file-level pass', () => lintFixtureFile(FIXTURE, 'things.yaml')],
];

for (const [pass, lint] of PASSES) {
  describe(`operation-operationId (${pass})`, () => {
    let violations;

    before(() => {
      violations = filterByRule(lint(), RULE);
    });

    it('produces no violation for an operation with an operationId', () => {
      assert.equal(filterByPathSegment(violations, '/valid/thing').length, 0);
    });

    it('does not treat path-level parameters as an operation', () => {
      assert.equal(filterByPathSegment(violations, 'parameters').length, 0);
    });

    it('flags an operation without an operationId as an error', () => {
      const v = filterByPathSegment(violations, '/missing/thing');
      assert.equal(v.length, 1);
      assert.equal(v[0].severity, ERROR);
    });

    it('flags an operation with an empty operationId as an error', () => {
      const v = filterByPathSegment(violations, '/empty/thing');
      assert.equal(v.length, 1);
      assert.equal(v[0].severity, ERROR);
    });

    it('produces exactly 2 violations total', () => {
      assert.equal(violations.length, 2);
    });
  });
}
