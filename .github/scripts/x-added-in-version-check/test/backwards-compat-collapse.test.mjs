import { test } from 'node:test';
import assert from 'node:assert/strict';
import { collapseBackwardsCompat, isTagless, normalizeQname } from '../backwards-compat-collapse.mjs';

const EP = 'POST /process-instances';

function entry(qualifiedName, version, { endpoint = EP, location = 'request' } = {}) {
  return { version, location, endpoint, property: qualifiedName.split('.').pop(), qualifiedName, path: [], children: [] };
}

function versionMapOf(entries) {
  return { properties: Object.fromEntries(entries.map((e) => [`${e.endpoint} > ${e.location} > ${e.qualifiedName}`, e])) };
}

const versionOf = (vm, qname, { endpoint = EP, location = 'request' } = {}) =>
  vm.properties[`${endpoint} > ${location} > ${qname}`].version;

test('a branch property with a flat pre-split form gets the flat version', () => {
  const vm = versionMapOf([entry('tenantId', '8.6'), entry('ByKey.tenantId', '8.8'), entry('ByTag.tenantId', '8.10')]);
  const collapsed = collapseBackwardsCompat(vm);
  assert.equal(versionOf(vm, 'ByKey.tenantId'), '8.6');
  assert.equal(versionOf(vm, 'ByTag.tenantId'), '8.6');
  assert.equal(collapsed.length, 2);
});

test('a |-tagged property with a flat pre-split form gets the flat version', () => {
  const vm = versionMapOf([entry('page.limit', '8.8'), entry('page|CursorPagination.limit', '8.9')]);
  collapseBackwardsCompat(vm);
  assert.equal(versionOf(vm, 'page|CursorPagination.limit'), '8.8');
});

test('a nested root union property with a flat pre-split form gets the flat version', () => {
  const vm = versionMapOf([entry('businessId', '8.9'), entry('Outer|Inner.businessId', '8.10')]);
  collapseBackwardsCompat(vm);
  assert.equal(versionOf(vm, 'Outer|Inner.businessId'), '8.9');
});

test('branch properties without a flat pre-split form keep their own versions', () => {
  // Left to the sibling-variant collapse, which checks the schemas.
  const vm = versionMapOf([entry('ByKey.businessId', '8.9'), entry('ByTag.businessId', '8.10')]);
  assert.deepEqual(collapseBackwardsCompat(vm), []);
  assert.equal(versionOf(vm, 'ByTag.businessId'), '8.10');
});

test('an inline-branch property is not grouped with a plain property of the same name', () => {
  // camunda/camunda#67989 review: this pass has no schema check, so grouping
  // oneOf[0].foo with a plain foo could give it a version that is too early.
  const vm = versionMapOf([entry('foo', '8.6'), entry('oneOf[0].foo', '8.10'), entry('anyOf[1].foo', '8.10')]);
  assert.deepEqual(collapseBackwardsCompat(vm), []);
  assert.equal(versionOf(vm, 'oneOf[0].foo'), '8.10');
  assert.equal(versionOf(vm, 'anyOf[1].foo'), '8.10');
});

test('a lowercase leading segment is an ordinary property, not a branch tag', () => {
  // filter.state and state are different properties.
  const vm = versionMapOf([entry('state', '8.6'), entry('filter.state', '8.8')]);
  assert.deepEqual(collapseBackwardsCompat(vm), []);
});

test('the same property on different endpoints or locations is not merged', () => {
  const vm = versionMapOf([
    entry('tenantId', '8.6', { endpoint: 'POST /other' }),
    entry('tenantId', '8.6', { location: 'response' }),
    entry('ByKey.tenantId', '8.8'),
  ]);
  assert.deepEqual(collapseBackwardsCompat(vm), []);
});

test('versions compare numerically, not as strings', () => {
  const vm = versionMapOf([entry('tenantId', '8.9'), entry('ByTag.tenantId', '8.10')]);
  collapseBackwardsCompat(vm);
  assert.equal(versionOf(vm, 'ByTag.tenantId'), '8.9');
});

test('isTagless and normalizeQname', () => {
  assert.equal(isTagless('foo'), true);
  assert.equal(isTagless('filter.state'), true);
  assert.equal(isTagless('oneOf[0].foo'), true);
  assert.equal(isTagless('ByKey.foo'), false);
  assert.equal(isTagless('page|CursorPagination.limit'), false);
  assert.equal(normalizeQname('ByKey.foo'), 'foo');
  assert.equal(normalizeQname('Outer|Inner.foo'), 'foo');
  assert.equal(normalizeQname('page|CursorPagination.limit'), 'page.limit');
  assert.equal(normalizeQname('oneOf[0].foo'), 'oneOf[0].foo');
});
