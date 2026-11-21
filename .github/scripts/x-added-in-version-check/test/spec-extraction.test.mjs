import { test } from 'node:test';
import assert from 'node:assert/strict';
import { extractProperties, extractSpecData } from '../spec-extraction.mjs';

// The qualified names produced here are what both collapse passes and the
// verifier group and look up by. These tests pin the format: if it changes,
// the hand-written names in the collapse tests no longer match real output.

const ref = (name) => ({ $ref: `#/components/schemas/${name}` });
const obj = (properties, extra = {}) => ({ type: 'object', properties, ...extra });
const str = { type: 'string' };

function qnames(spec, schema) {
  return [...extractProperties(spec, schema, 0, 3, ['root']).keys()].sort();
}

test('top-level and nested object properties are dotted', () => {
  const schema = obj({ id: str, user: obj({ address: obj({ zip: str }) }) });
  assert.deepEqual(qnames({}, schema), ['id', 'user', 'user.address', 'user.address.zip']);
});

test('array item properties are marked with []', () => {
  const schema = obj({ tenants: { type: 'array', items: obj({ name: str }) } });
  assert.deepEqual(qnames({}, schema), ['tenants', 'tenants[].name']);
});

test('properties merged from allOf carry no branch tag', () => {
  const spec = { components: { schemas: { Base: obj({ id: str }) } } };
  const schema = { allOf: [ref('Base'), obj({ extra: str })] };
  assert.deepEqual(qnames(spec, schema), ['extra', 'id']);
});

test('root $ref oneOf branches are tagged with the schema name as a leading segment', () => {
  const spec = { components: { schemas: { ByKey: obj({ key: str, tenantId: str }), ById: obj({ id: str, tenantId: str }) } } };
  const schema = { oneOf: [ref('ByKey'), ref('ById')] };
  assert.deepEqual(qnames(spec, schema), ['ById.id', 'ById.tenantId', 'ByKey.key', 'ByKey.tenantId']);
});

test('root inline oneOf/anyOf branches are tagged with keyword and index', () => {
  const schema = { oneOf: [obj({ foo: str })], anyOf: [obj({ bar: str }), obj({ bar: str })] };
  assert.deepEqual(qnames({}, schema), ['anyOf[0].bar', 'anyOf[1].bar', 'oneOf[0].foo']);
});

test('branches under a property are tagged with |Branch on that property', () => {
  const spec = { components: { schemas: { OffsetPagination: obj({ limit: str }), CursorPagination: obj({ limit: str }) } } };
  const schema = obj({ page: { oneOf: [ref('OffsetPagination'), ref('CursorPagination')] } });
  assert.deepEqual(qnames(spec, schema), ['page', 'page|CursorPagination.limit', 'page|OffsetPagination.limit']);
});

test('a root union nested in a root union carries both tags', () => {
  const spec = { components: { schemas: { Outer: { oneOf: [ref('Inner')] }, Inner: obj({ businessId: str }) } } };
  assert.deepEqual(qnames(spec, { oneOf: [ref('Outer')] }), ['Outer|Inner.businessId']);
});

test('a $ref branch property points at the referenced schema in the spec', () => {
  const spec = { components: { schemas: { ByKey: obj({ key: str }) } } };
  const props = extractProperties(spec, { oneOf: [ref('ByKey')] }, 0, 3, ['root']);
  assert.deepEqual(props.get('ByKey.key').path, ['components', 'schemas', 'ByKey', 'properties', 'key']);
});

test('an inline branch property points into the branch', () => {
  const props = extractProperties({}, { oneOf: [obj({ foo: str })] }, 0, 3, ['root']);
  assert.deepEqual(props.get('oneOf[0].foo').path, ['root', 'oneOf', '0', 'properties', 'foo']);
});

test('nesting stops at the depth limit', () => {
  const schema = obj({ a: obj({ b: obj({ c: obj({ d: obj({ e: str }) }) }) }) });
  assert.deepEqual(qnames({}, schema), ['a', 'a.b', 'a.b.c', 'a.b.c.d']);
});

test('a recursive schema terminates', () => {
  const spec = { components: { schemas: { Node: obj({ name: str, children: { type: 'array', items: ref('Node') } }) } } };
  assert.deepEqual(qnames(spec, ref('Node')), ['children', 'name']);
});

test('extractSpecData keys properties by endpoint, location and qualified name', () => {
  const spec = {
    paths: {
      '/widgets': {
        post: {
          summary: 'Create a widget',
          requestBody: { content: { 'application/json': { schema: ref('WidgetRequest') } } },
          responses: { 200: { content: { 'application/json': { schema: ref('Widget') } } } },
        },
      },
    },
    components: { schemas: { WidgetRequest: obj({ name: str }), Widget: obj({ key: str }) } },
  };
  const { operations, properties } = extractSpecData(spec);
  assert.deepEqual([...operations.keys()], ['POST /widgets']);
  assert.deepEqual([...properties.keys()].sort(), ['POST /widgets > request > name', 'POST /widgets > response > key']);
  assert.equal(properties.get('POST /widgets > request > name').property, 'name');
});

test('a property from another source file gets that filename as a path prefix', () => {
  const spec = {
    paths: { '/widgets': { post: { requestBody: { content: { 'application/json': { schema: ref('WidgetRequest') } } } } } },
    components: { schemas: { WidgetRequest: obj({ name: str }) } },
  };
  const { properties } = extractSpecData(
    spec,
    new Map([['POST /widgets', 'widgets.yaml']]),
    new Map([['WidgetRequest', 'widget-schemas.yaml']]),
  );
  assert.deepEqual(properties.get('POST /widgets > request > name').path,
    ['widget-schemas.yaml', 'components', 'schemas', 'WidgetRequest', 'properties', 'name']);
});
