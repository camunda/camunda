'use strict';

const { describe, it, before } = require('node:test');
const assert = require('node:assert/strict');
const { lintFixture, filterByRule, filterByPathSegment } = require('./helpers');

const FIXTURE = 'present-when-referential';
const RULE = 'present-when-request-resolves';

describe('present-when-request-resolves', () => {
  let violations;

  before(() => {
    // Unlike `present-when-shape`, this rule resolves each operation and
    // cross-references the marker's `request` against the operation's request
    // body, so the fixture carries full operations (requestBody + 2xx
    // responses). Each operation isolates one scenario; we filter by its path.
    violations = filterByRule(lintFixture(FIXTURE), RULE);
  });

  const forPath = (segment) => filterByPathSegment(violations, segment);

  // ── Valid: request resolves, marker nested under an array, nullable+required.

  it('accepts a marker whose request resolves to a top-level request field', () => {
    assert.equal(forPath('/valid').length, 0);
  });

  // ── Invalid scenarios (each isolated on its own operation) ───────

  it('flags a marker whose request names no request-body property', () => {
    const v = forPath('/typo');
    assert.equal(v.length, 1);
    assert.match(v[0].message, /withLeese/);
    assert.match(v[0].message, /does not resolve to a top-level property/);
  });

  it('flags a marker on an operation with no request body', () => {
    const v = forPath('/no-request-body');
    assert.equal(v.length, 1);
    assert.match(v[0].message, /does not resolve to a top-level property/);
  });

  it('flags a marked response property that is not nullable', () => {
    const v = forPath('/not-nullable');
    assert.equal(v.length, 1);
    assert.match(v[0].message, /nullable/);
  });

  it('flags a marked response property missing from `required`', () => {
    const v = forPath('/not-required');
    assert.equal(v.length, 1);
    assert.match(v[0].message, /required/);
  });
});
