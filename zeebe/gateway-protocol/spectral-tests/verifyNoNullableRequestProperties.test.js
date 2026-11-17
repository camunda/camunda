'use strict';

const { describe, it, before } = require('node:test');
const assert = require('node:assert/strict');
const { lintFixture, filterByRule } = require('./helpers');

const RULE = 'no-nullable-request-properties';
const FIXTURE = 'request-nullable';

// The reported path is anchored at the operation (Spectral trims the synthetic
// `requestBody/<fieldPath>` suffix when mapping back to the source), so the
// field path is read from the message.
const fieldPathOf = (v) => /^Request body field `(.*?)` of `/.exec(v.message)[1];

describe('verifyNoNullableRequestProperties', () => {
  let violations;

  // Violations of one operation, identified by the API path segment of the
  // result path, which is anchored at `paths./<api-path>.<method>`.
  const forApiPath = (apiPath) => violations.filter((v) => v.path[1] === apiPath);

  before(() => {
    violations = filterByRule(lintFixture(FIXTURE), RULE);
  });

  // ── Valid cases (should produce zero violations) ─────────────────

  describe('valid: optional non-nullable request fields, nullable response', () => {
    it('produces no violations', () => {
      assert.equal(forApiPath('/valid-post').length, 0);
    });
  });

  describe('valid: allowlisted field of an allowlisted operation', () => {
    it('does not flag changeset.dueDate of allowlistedPatch', () => {
      const v = forApiPath('/allowlisted-patch/{thingKey}').filter(
        (e) => fieldPathOf(e) === 'changeset.dueDate'
      );
      assert.equal(v.length, 0);
    });
  });

  describe('valid: allowlisted nullable body schema', () => {
    it('does not flag the root of allowlistedRoot', () => {
      assert.equal(forApiPath('/allowlisted-root').length, 0);
    });
  });

  // ── Invalid cases ────────────────────────────────────────────────

  describe('invalid: top-level nullable field', () => {
    it('flags the field', () => {
      const v = forApiPath('/invalid-top-level');
      assert.equal(v.length, 1);
      assert.equal(fieldPathOf(v[0]), 'name');
      assert.match(v[0].message, /`name` of `invalidTopLevel` must not be `nullable`/);
    });
  });

  describe('invalid: nullable field on a PATCH operation', () => {
    it('flags the field (no PATCH exemption)', () => {
      const v = forApiPath('/invalid-patch');
      assert.equal(v.length, 1);
      assert.equal(fieldPathOf(v[0]), 'label');
    });
  });

  describe('invalid: nullable field in a $ref-ed component', () => {
    it('flags the field with its full path', () => {
      const v = forApiPath('/invalid-ref');
      assert.equal(v.length, 1);
      assert.equal(fieldPathOf(v[0]), 'nested.value');
    });
  });

  describe('invalid: nullable field in an allOf member', () => {
    it('flags the field under the parent path', () => {
      const v = forApiPath('/invalid-allof');
      assert.equal(v.length, 1);
      assert.equal(fieldPathOf(v[0]), 'extra');
    });
  });

  describe('invalid: nullable field in a oneOf member', () => {
    it('flags the field under the parent path', () => {
      const v = forApiPath('/invalid-oneof');
      assert.equal(v.length, 1);
      assert.equal(fieldPathOf(v[0]), 'extra');
    });
  });

  describe('invalid: nullable field in an anyOf member', () => {
    it('flags the field under the parent path', () => {
      const v = forApiPath('/invalid-anyof');
      assert.equal(v.length, 1);
      assert.equal(fieldPathOf(v[0]), 'extra');
    });
  });

  describe('invalid: nullable body schema', () => {
    it('flags the body itself', () => {
      const v = forApiPath('/invalid-root');
      assert.equal(v.length, 1);
      assert.match(v[0].message, /Request body of `invalidRoot` must not be `nullable`/);
    });
  });

  describe('invalid: nullable allOf member at the body root', () => {
    it('flags the body itself once', () => {
      const v = forApiPath('/invalid-root-allof');
      assert.equal(v.length, 1);
      assert.match(v[0].message, /Request body of `invalidRootAllOf` must not be `nullable`/);
    });
  });

  describe('invalid: nullable field in array items', () => {
    it('flags the field with the [] item marker', () => {
      const v = forApiPath('/invalid-items');
      assert.equal(v.length, 1);
      assert.equal(fieldPathOf(v[0]), 'list[].field');
    });
  });

  describe('invalid: nullable field in additionalProperties', () => {
    it('flags the field with the {} value marker', () => {
      const v = forApiPath('/invalid-additional-properties');
      assert.equal(v.length, 1);
      assert.equal(fieldPathOf(v[0]), 'map{}.field');
    });
  });

  describe('allowlist is per field path', () => {
    it('flags only the field of allowlistedPatch that is not allowlisted', () => {
      const v = forApiPath('/allowlisted-patch/{thingKey}');
      assert.equal(v.length, 1);
      assert.equal(fieldPathOf(v[0]), 'changeset.notListed');
    });
  });

  describe('allowlist is per operation', () => {
    it('flags every nullable field of a shared schema on a non-allowlisted operation', () => {
      const v = forApiPath('/invalid-shared');
      assert.deepEqual(v.map(fieldPathOf).sort(), [
        'changeset.dueDate',
        'changeset.notListed',
      ]);
    });
  });

  describe('invalid: self-referencing schema', () => {
    it('terminates and reports the nullable field once', () => {
      const v = forApiPath('/invalid-recursive');
      assert.deepEqual(v.map(fieldPathOf), ['node.value']);
    });
  });
});
