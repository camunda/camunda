import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mergeVersion } from '../version-map-merge.mjs';

const EP = 'POST /widgets';

const emptyMap = () => ({ operations: {}, properties: {}, deletedOperations: {}, deletedProperties: {} });

const op = (path = ['paths', '/widgets', 'post'], summary = 'Create a widget') => ({ summary, path });

function prop(qualifiedName, { location = 'request', path = ['p', qualifiedName] } = {}) {
  return { location, endpoint: EP, property: qualifiedName.split(/[.|]/).pop(), qualifiedName, depth: 0, path };
}

/** One version's extracted spec data: { operations, properties } as Maps. */
function versionData(props, ops = { [EP]: op() }) {
  return {
    operations: new Map(Object.entries(ops)),
    properties: new Map(props.map((p) => [`${EP} > ${p.location} > ${p.qualifiedName}`, p])),
  };
}

/** Merge versions in order, as the builder does. */
function mergeAll(...versions) {
  const vm = emptyMap();
  let previous = { operations: null, properties: null };
  const results = [];
  for (const [version, data] of versions) {
    results.push(mergeVersion(vm, version, data, previous));
    previous = data;
  }
  return { vm, results };
}

test('operations and properties keep the version they were first seen in', () => {
  const { vm, results } = mergeAll(
    ['8.6', versionData([prop('name')])],
    ['8.7', versionData([prop('name'), prop('tenantId')])],
  );
  assert.equal(vm.operations[EP].version, '8.6');
  assert.equal(vm.properties[`${EP} > request > name`].version, '8.6');
  assert.equal(vm.properties[`${EP} > request > tenantId`].version, '8.7');
  assert.deepEqual(results[1], { newOps: 0, newProps: 1, deletedOps: 0, deletedProps: 0 });
});

test('paths are refreshed to the latest version without changing the version', () => {
  const { vm } = mergeAll(
    ['8.6', versionData([prop('name', { path: ['old'] })], { [EP]: op(['old-op']) })],
    ['8.8', versionData([prop('name', { path: ['widgets.yaml', 'new'] })], { [EP]: op(['widgets.yaml', 'new-op']) })],
  );
  assert.deepEqual(vm.properties[`${EP} > request > name`].path, ['widgets.yaml', 'new']);
  assert.equal(vm.properties[`${EP} > request > name`].version, '8.6');
  assert.deepEqual(vm.operations[EP].path, ['widgets.yaml', 'new-op']);
});

test('a missing summary is backfilled from a later version', () => {
  const { vm } = mergeAll(
    ['8.6', versionData([], { [EP]: op(undefined, '') })],
    ['8.7', versionData([], { [EP]: op(undefined, 'Create a widget') })],
  );
  assert.equal(vm.operations[EP].summary, 'Create a widget');
});

test('a removed operation is recorded as deleted in the version it disappeared, and loses its stale path', () => {
  const { vm } = mergeAll(['8.6', versionData([])], ['8.7', versionData([], {})]);
  assert.deepEqual(vm.deletedOperations[EP], { removedIn: '8.7', summary: 'Create a widget' });
  assert.equal(vm.operations[EP].version, '8.6');
  assert.equal('path' in vm.operations[EP], false);
});

test('a removed property is recorded as deleted and loses its stale path', () => {
  const { vm } = mergeAll(['8.6', versionData([prop('legacy')])], ['8.7', versionData([])]);
  assert.deepEqual(vm.deletedProperties[`${EP} > request > legacy`], { removedIn: '8.7', endpoint: EP });
  assert.equal('path' in vm.properties[`${EP} > request > legacy`], false);
});

test('a deletion is recorded once, in the first version without the property', () => {
  const { vm } = mergeAll(['8.6', versionData([prop('legacy')])], ['8.7', versionData([])], ['8.8', versionData([])]);
  assert.equal(vm.deletedProperties[`${EP} > request > legacy`].removedIn, '8.7');
});

test('a property moved into a container or a root branch is still present', () => {
  // processDefinitionVersion moved from the 8.7 request root into
  // ProcessInstanceCreationInstructionById in 8.8: same leaf, no | tag.
  const { vm } = mergeAll(
    ['8.7', versionData([prop('processDefinitionVersion')])],
    ['8.8', versionData([prop('ById.processDefinitionVersion')])],
  );
  assert.deepEqual(vm.deletedProperties, {});
});

test('a property moved into a |-tagged branch is deleted from its old position', () => {
  const { vm } = mergeAll(
    ['8.7', versionData([prop('filter.state')])],
    ['8.8', versionData([prop('filter|AdvancedFilter.state')])],
  );
  assert.equal(vm.deletedProperties[`${EP} > request > filter.state`].removedIn, '8.8');
  assert.equal(vm.properties[`${EP} > request > filter|AdvancedFilter.state`].version, '8.8');
});

test('a request property does not keep a removed response property alive', () => {
  const { vm } = mergeAll(
    ['8.6', versionData([prop('key', { location: 'response' }), prop('key')])],
    ['8.7', versionData([prop('key')])],
  );
  assert.equal(vm.deletedProperties[`${EP} > response > key`].removedIn, '8.7');
});
