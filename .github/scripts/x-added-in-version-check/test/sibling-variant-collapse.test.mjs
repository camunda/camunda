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
  assert.equal(normalizeQname('oneOf[0].foo'), 'foo');
  assert.equal(normalizeQname('anyOf[2].bar.baz'), 'bar.baz');
  assert.equal(normalizeQname('plain'), 'plain');
  // A lowercase leading segment is an ordinary property, not a branch tag.
  assert.equal(normalizeQname('filter.state'), 'filter.state');
  // Doc keys are stripped from schema objects; a doc-keyed object held as
  // literal data (e.g. a `default` value) is compared verbatim.
  assert.equal(
    schemaShape({ b: 1, properties: { a: { description: 'x', type: 'string' } } }),
    '{"b":1,"properties":{"a":{"type":"string"}}}',
  );
  assert.equal(schemaShape({ default: { description: 'x' } }), '{"default":{"description":"x"}}');
  assert.equal(schemaShape(undefined), undefined);
});

test('a property repeated on inline root oneOf branches gets the earliest sibling version', () => {
  // Inline (unreferenced) root branches are tagged with their keyword and
  // index (`oneOf[0].foo`), not a schema name.
  const inline = (index, version) => ({
    version,
    location: 'request',
    endpoint: EP,
    property: 'businessId',
    qualifiedName: `oneOf[${index}].businessId`,
    path: ['components', 'schemas', 'InlineRoot', 'oneOf', String(index), 'properties', 'businessId'],
    children: [],
  });
  const vm = versionMapOf([inline(0, '8.9'), inline(1, '8.10')]);
  const spec = {
    components: {
      schemas: {
        InlineRoot: {
          oneOf: [
            { type: 'object', properties: { businessId: BUSINESS_ID } },
            { type: 'object', properties: { businessId: BUSINESS_ID } },
          ],
        },
      },
    },
  };
  const { collapsed } = collapseSiblingVariants(vm, spec);
  assert.equal(vm.properties[`${EP} > request > oneOf[1].businessId`].version, '8.9');
  assert.deepEqual(collapsed, [{ propKey: `${EP} > request > oneOf[1].businessId`, from: '8.10', to: '8.9' }]);
});

test('a property named like a documentation keyword keeps its type as a difference', () => {
  // `description` is a *property name* here, not a schema keyword: two
  // branches whose `description` property has a different type are different
  // schemas and must not collapse.
  const vm = versionMapOf([
    entry(BY_ID, 'description', '8.9'),
    entry(BY_TAG, 'description', '8.10'),
  ]);
  const spec = specOf({
    [BY_ID]: { description: { type: 'string' } },
    [BY_TAG]: { description: { type: 'boolean' } },
  });
  const { collapsed, skipped } = collapseSiblingVariants(vm, spec);
  assert.equal(vm.properties[`${EP} > request > ${BY_TAG}.description`].version, '8.10');
  assert.deepEqual(collapsed, []);
  assert.deepEqual(skipped, [`${EP} > request > description`]);
});

test('literal data inside default/const/enum is compared verbatim', () => {
  // An object-valued `default` whose keys look like documentation keywords is
  // data, not documentation — dropping them would collapse different schemas.
  const vm = versionMapOf([
    entry(BY_ID, 'config', '8.9'),
    entry(BY_TAG, 'config', '8.10'),
  ]);
  const spec = specOf({
    [BY_ID]: { config: { type: 'object', default: { description: 'a', retries: 1 } } },
    [BY_TAG]: { config: { type: 'object', default: { description: 'b', retries: 1 } } },
  });
  const { collapsed } = collapseSiblingVariants(vm, spec);
  assert.equal(vm.properties[`${EP} > request > ${BY_TAG}.config`].version, '8.10');
  assert.deepEqual(collapsed, []);
});

test('a filename-prefixed cross-file path is looked up in the bundle without the prefix', () => {
  // The builder prefixes cross-file property paths with the source YAML
  // filename for the verifier; the bundled spec has no such top-level key.
  const prefixed = (branch, version) => ({
    ...entry(branch, 'businessId', version),
    path: ['identifiers.yaml', 'components', 'schemas', branch, 'properties', 'businessId'],
  });
  const vm = versionMapOf([prefixed(BY_ID, '8.9'), prefixed(BY_TAG, '8.10')]);
  const spec = specOf({
    [BY_ID]: { businessId: BUSINESS_ID },
    [BY_TAG]: { businessId: BUSINESS_ID },
  });
  const { collapsed } = collapseSiblingVariants(vm, spec);
  assert.equal(vm.properties[`${EP} > request > ${BY_TAG}.businessId`].version, '8.9');
  assert.deepEqual(collapsed, [{ propKey: `${EP} > request > ${BY_TAG}.businessId`, from: '8.10', to: '8.9' }]);
});

test('paths that cross an intermediate $ref node are followed', () => {
  // The bundler emits refs into `#/paths/…`; traversal passes through nodes
  // that are themselves `{ $ref }`.
  const viaPath = (branch, version) => ({
    ...entry(branch, 'businessId', version),
    path: ['paths', '/process-instances', 'post', 'requestBody', 'content', 'application/json', 'schema', 'properties', 'businessId'],
  });
  const vm = versionMapOf([viaPath(BY_ID, '8.9'), viaPath(BY_TAG, '8.10')]);
  const spec = {
    paths: {
      '/process-instances': {
        post: {
          requestBody: {
            content: {
              'application/json': {
                schema: { $ref: '#/components/schemas/CreateRequest' },
              },
            },
          },
        },
      },
    },
    components: {
      schemas: {
        CreateRequest: { type: 'object', properties: { businessId: BUSINESS_ID } },
      },
    },
  };
  const { collapsed } = collapseSiblingVariants(vm, spec);
  assert.deepEqual(collapsed, [{ propKey: `${EP} > request > ${BY_TAG}.businessId`, from: '8.10', to: '8.9' }]);
});
