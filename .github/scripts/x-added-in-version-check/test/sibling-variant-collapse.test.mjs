import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  collapseSiblingVariants,
  normalizeQname,
  schemaShape,
} from '../sibling-variant-collapse.mjs';

const EP = 'POST /process-instances';
const BY_ID = 'ProcessInstanceCreationInstructionById';
const BY_KEY = 'ProcessInstanceCreationInstructionByKey';
const BY_TAG = 'ProcessInstanceCreationInstructionByVersionTag';

/** A version-map entry for `<Branch>.<prop>` on the create-process-instance request. */
function entry(branch, prop, version, { endpoint = EP, location = 'request', qname } = {}) {
  return {
    version,
    location,
    endpoint,
    property: prop.split('.').pop(),
    qualifiedName: qname ?? `${branch}.${prop}`,
    path: ['components', 'schemas', branch, 'properties', prop],
    children: [],
  };
}

function key(e) {
  return `${e.endpoint} > ${e.location} > ${e.qualifiedName}`;
}

function versionMapOf(entries, deleted = {}) {
  return {
    properties: Object.fromEntries(entries.map((e) => [key(e), e])),
    deletedProperties: deleted,
  };
}

/** A bundled spec in which each branch has the given properties. */
function specOf(branches) {
  const schemas = {};
  for (const [name, properties] of Object.entries(branches)) schemas[name] = { type: 'object', properties };
  return { components: { schemas } };
}

const BUSINESS_ID = { $ref: '#/components/schemas/BusinessId' };

test('a property repeated on a new sibling branch gets the earliest sibling version', () => {
  const vm = versionMapOf([
    entry(BY_ID, 'businessId', '8.9'),
    entry(BY_KEY, 'businessId', '8.9'),
    entry(BY_TAG, 'businessId', '8.10'),
  ]);
  const spec = specOf({
    [BY_ID]: { businessId: BUSINESS_ID },
    [BY_KEY]: { businessId: BUSINESS_ID },
    [BY_TAG]: { businessId: BUSINESS_ID },
  });
  const { collapsed, skipped } = collapseSiblingVariants(vm, spec);
  assert.equal(vm.properties[`${EP} > request > ${BY_TAG}.businessId`].version, '8.9');
  assert.deepEqual(collapsed, [{ propKey: `${EP} > request > ${BY_TAG}.businessId`, from: '8.10', to: '8.9' }]);
  assert.deepEqual(skipped, []);
});

test('a property with a different schema on the new branch keeps its own version', () => {
  const vm = versionMapOf([
    entry(BY_ID, 'businessId', '8.9'),
    entry(BY_TAG, 'businessId', '8.10'),
  ]);
  const spec = specOf({
    [BY_ID]: { businessId: BUSINESS_ID },
    [BY_TAG]: { businessId: { type: 'string' } },
  });
  const { collapsed, skipped } = collapseSiblingVariants(vm, spec);
  assert.equal(vm.properties[`${EP} > request > ${BY_TAG}.businessId`].version, '8.10');
  assert.deepEqual(collapsed, []);
  assert.deepEqual(skipped, [`${EP} > request > businessId`]);
});

test('documentation and x- extensions do not count as a schema difference', () => {
  const vm = versionMapOf([
    entry(BY_ID, 'tags', '8.8'),
    entry(BY_TAG, 'tags', '8.10'),
  ]);
  const spec = specOf({
    [BY_ID]: { tags: { type: 'array', items: { type: 'string' }, description: 'Tags.', example: ['a'] } },
    [BY_TAG]: { tags: { description: 'Other words.', 'x-foo': 1, items: { type: 'string' }, type: 'array' } },
  });
  collapseSiblingVariants(vm, spec);
  assert.equal(vm.properties[`${EP} > request > ${BY_TAG}.tags`].version, '8.8');
});

test('a discriminator pinned to a different value on each branch is not collapsed', () => {
  const ep = 'POST /things';
  const vm = versionMapOf([
    entry('CatThing', 'kind', '8.8', { endpoint: ep }),
    entry('DogThing', 'kind', '8.10', { endpoint: ep }),
  ]);
  const spec = specOf({
    CatThing: { kind: { type: 'string', enum: ['cat'] } },
    DogThing: { kind: { type: 'string', enum: ['dog'] } },
  });
  collapseSiblingVariants(vm, spec);
  assert.equal(vm.properties[`${ep} > request > DogThing.kind`].version, '8.10');
});

test('nested properties under a repeated branch property are collapsed too', () => {
  const nested = (branch, version) => ({
    ...entry(branch, 'runtimeInstructions', version),
    property: 'afterElementId',
    qualifiedName: `${branch}.runtimeInstructions[]|ProcessInstanceCreationTerminateInstruction.afterElementId`,
    path: ['components', 'schemas', 'ProcessInstanceCreationTerminateInstruction', 'properties', 'afterElementId'],
  });
  const vm = versionMapOf([nested(BY_ID, '8.8'), nested(BY_TAG, '8.10')]);
  const spec = specOf({ ProcessInstanceCreationTerminateInstruction: { afterElementId: { type: 'string' } } });
  collapseSiblingVariants(vm, spec);
  for (const e of Object.values(vm.properties)) assert.equal(e.version, '8.8');
});

test('properties that are not on a oneOf/anyOf branch are left alone', () => {
  const plain = entry('Ignored', 'variables', '8.10', { qname: 'variables' });
  const vm = versionMapOf([plain, entry(BY_ID, 'variables', '8.8')]);
  const spec = specOf({ Ignored: { variables: { type: 'object' } }, [BY_ID]: { variables: { type: 'object' } } });
  const { collapsed } = collapseSiblingVariants(vm, spec);
  assert.equal(vm.properties[`${EP} > request > variables`].version, '8.10');
  assert.deepEqual(collapsed, []);
});

test('the same property on different endpoints or locations is not merged', () => {
  const vm = versionMapOf([
    entry(BY_ID, 'businessId', '8.9'),
    entry(BY_TAG, 'businessId', '8.10', { endpoint: 'POST /other' }),
    entry(BY_KEY, 'businessId', '8.10', { location: 'response' }),
  ]);
  const spec = specOf({
    [BY_ID]: { businessId: BUSINESS_ID },
    [BY_KEY]: { businessId: BUSINESS_ID },
    [BY_TAG]: { businessId: BUSINESS_ID },
  });
  const { collapsed } = collapseSiblingVariants(vm, spec);
  assert.deepEqual(collapsed, []);
});

test('deleted properties take no part in the collapse', () => {
  const old = entry(BY_KEY, 'businessId', '8.6');
  const vm = versionMapOf([old, entry(BY_ID, 'businessId', '8.9'), entry(BY_TAG, 'businessId', '8.10')], {
    [key(old)]: { removedIn: '8.8', endpoint: EP },
  });
  const spec = specOf({ [BY_KEY]: { businessId: BUSINESS_ID }, [BY_ID]: { businessId: BUSINESS_ID }, [BY_TAG]: { businessId: BUSINESS_ID } });
  collapseSiblingVariants(vm, spec);
  assert.equal(vm.properties[`${EP} > request > ${BY_TAG}.businessId`].version, '8.9');
});

test('a property whose schema cannot be found is not collapsed', () => {
  const vm = versionMapOf([entry(BY_ID, 'businessId', '8.9'), entry(BY_TAG, 'businessId', '8.10')]);
  const spec = specOf({ [BY_ID]: { businessId: BUSINESS_ID } });
  collapseSiblingVariants(vm, spec);
  assert.equal(vm.properties[`${EP} > request > ${BY_TAG}.businessId`].version, '8.10');
});

test('versions compare numerically, not as strings', () => {
  const vm = versionMapOf([entry(BY_ID, 'tags', '8.10'), entry(BY_TAG, 'tags', '8.9')]);
  const spec = specOf({ [BY_ID]: { tags: { type: 'string' } }, [BY_TAG]: { tags: { type: 'string' } } });
  collapseSiblingVariants(vm, spec);
  assert.equal(vm.properties[`${EP} > request > ${BY_ID}.tags`].version, '8.9');
});

test('normalizeQname and schemaShape', () => {
  assert.equal(normalizeQname(`${BY_TAG}.runtimeInstructions[]|Foo.type`), 'runtimeInstructions[].type');
  assert.equal(normalizeQname('page|OffsetPagination.limit'), 'page.limit');
  assert.equal(schemaShape({ b: 1, a: { description: 'x', c: 2 } }), '{"a":{"c":2},"b":1}');
  assert.equal(schemaShape(undefined), undefined);
});
