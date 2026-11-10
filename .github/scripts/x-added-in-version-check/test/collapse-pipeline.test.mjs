import { test } from 'node:test';
import assert from 'node:assert/strict';
import { collapseBackwardsCompat } from '../backwards-compat-collapse.mjs';
import { collapseSiblingVariants } from '../sibling-variant-collapse.mjs';

// Both passes in the builder's order. Each pass is safe on its own; this
// checks that they are also safe together: a version is only ever lowered
// for a flat pre-split form, or for an identical schema on sibling branches.

const EP = 'POST /widgets';

function entry(qualifiedName, version, path) {
  return { version, location: 'request', endpoint: EP, property: qualifiedName.split('.').pop(), qualifiedName, path, children: [] };
}

function runBoth(entries, spec) {
  const vm = { properties: Object.fromEntries(entries.map((e) => [`${EP} > request > ${e.qualifiedName}`, e])), deletedProperties: {} };
  collapseBackwardsCompat(vm);
  collapseSiblingVariants(vm, spec);
  return (qname) => vm.properties[`${EP} > request > ${qname}`].version;
}

const body = (schema) => ['paths', '/widgets', 'post', 'requestBody', 'content', 'application/json', 'schema', ...schema];

test('an inline-branch property with a different schema than a plain one keeps its version', () => {
  const spec = {
    paths: { '/widgets': { post: { requestBody: { content: { 'application/json': { schema: {
      properties: { foo: { type: 'string' } },
      oneOf: [{ properties: { foo: { type: 'integer' } } }, { properties: { bar: { type: 'string' } } }],
    } } } } } } },
  };
  const versionOf = runBoth(
    [entry('foo', '8.6', body(['properties', 'foo'])), entry('oneOf[0].foo', '8.10', body(['oneOf', 0, 'properties', 'foo']))],
    spec,
  );
  assert.equal(versionOf('oneOf[0].foo'), '8.10');
});

test('identical inline siblings still collapse to the earliest version', () => {
  const foo = { type: 'string' };
  const spec = {
    paths: { '/widgets': { post: { requestBody: { content: { 'application/json': { schema: {
      oneOf: [{ properties: { foo } }, { properties: { foo } }],
    } } } } } } },
  };
  const versionOf = runBoth(
    [entry('oneOf[0].foo', '8.8', body(['oneOf', 0, 'properties', 'foo'])), entry('oneOf[1].foo', '8.10', body(['oneOf', 1, 'properties', 'foo']))],
    spec,
  );
  assert.equal(versionOf('oneOf[1].foo'), '8.8');
});
