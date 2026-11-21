import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import YAML from 'yaml';
import { verifySpecs } from '../verify-specs.mjs';

// verifySpecs decides the PR check's outcome. Each test writes a small
// multi-file spec to a temporary directory, with a version map and endpoint
// map as the builder would produce them, and asserts on the reported issues.

const FILE = 'widgets.yaml';
const POST = 'POST /widgets';
const GET = 'GET /widgets';
const opPath = (method) => [FILE, 'paths', '/widgets', method];
const propPath = (schema, name) => [FILE, 'components', 'schemas', schema, 'properties', name];

function run(t, files, versionMap, endpointMap = { [POST]: FILE, [GET]: FILE }) {
  const specDir = mkdtempSync(join(tmpdir(), 'verify-specs-'));
  t.after(() => rmSync(specDir, { recursive: true, force: true }));
  for (const [name, doc] of Object.entries(files)) writeFileSync(join(specDir, name), YAML.stringify(doc));
  const vm = { operations: {}, properties: {}, deletedOperations: {}, deletedProperties: {}, ...versionMap };
  return verifySpecs({ specDir, versionMap: vm, endpointMap });
}

/** widgets.yaml with POST and GET /widgets, both using schema Widget. */
function widgetsYaml({ post = {}, get = {}, widget = {} } = {}) {
  return {
    paths: {
      '/widgets': {
        post: { 'x-added-in-version': '8.6', requestBody: { $ref: '#/components/schemas/Widget' }, ...post },
        get: { 'x-added-in-version': '8.6', ...get },
      },
    },
    components: { schemas: { Widget: { type: 'object', properties: { name: { type: 'string' }, tenantId: { type: 'string' } }, ...widget } } },
  };
}

const ops = (postVersion = '8.6', getVersion = '8.6') => ({
  [POST]: { version: postVersion, path: opPath('post') },
  [GET]: { version: getVersion, path: opPath('get') },
});

function prop(name, version, { endpoint = POST, schema = 'Widget', children = [] } = {}) {
  return { version, endpoint, location: 'request', property: name, qualifiedName: name, path: propPath(schema, name), children };
}

const issues = (result) => [...result.opErrors, ...result.propErrors].map((e) => e.issue).sort();

// ── Operations ──────────────────────────────────────────────────────────────

test('operations annotated with their version pass', (t) => {
  const result = run(t, { [FILE]: widgetsYaml() }, { operations: ops() });
  assert.deepEqual(issues(result), []);
  assert.equal(result.opOk, 2);
});

test('an operation without x-added-in-version is reported', (t) => {
  const result = run(t, { [FILE]: widgetsYaml({ get: { 'x-added-in-version': undefined } }) }, { operations: ops() });
  assert.deepEqual(result.opErrors.map((e) => [e.op, e.issue, e.expected]), [[GET, 'MISSING_X_ADDED_IN_VERSION', '8.6']]);
});

test('an operation annotated with the wrong version is reported', (t) => {
  const result = run(t, { [FILE]: widgetsYaml() }, { operations: ops('8.6', '8.8') });
  assert.deepEqual(result.opErrors.map((e) => [e.op, e.issue, e.expected, e.actual]), [[GET, 'VERSION_MISMATCH', '8.8', '8.6']]);
});

test('x-added-in-version-override replaces the computed operation version', (t) => {
  const yaml = widgetsYaml({ get: { 'x-added-in-version': '8.7', 'x-added-in-version-override': '8.7' } });
  const result = run(t, { [FILE]: yaml }, { operations: ops('8.6', '8.6') });
  assert.deepEqual(issues(result), []);
});

test('an operation the endpoint map does not know is reported', (t) => {
  const result = run(t, { [FILE]: widgetsYaml() }, { operations: ops() }, { [POST]: FILE });
  assert.deepEqual(result.opErrors.map((e) => [e.op, e.issue]), [[GET, 'NO_ENDPOINT_MAP_ENTRY']]);
});

test('an endpoint map entry pointing at a missing file, path or method is reported', (t) => {
  const yaml = widgetsYaml();
  delete yaml.paths['/widgets'].get;
  const result = run(t, { [FILE]: yaml }, {
    operations: { ...ops(), 'GET /gone': { version: '8.6' }, 'PUT /widgets': { version: '8.6' } },
  }, { [POST]: FILE, [GET]: FILE, 'GET /gone': FILE, 'PUT /widgets': 'missing.yaml' });
  assert.deepEqual(result.opErrors.map((e) => [e.op, e.issue]).sort(), [
    ['GET /gone', 'PATH_NOT_IN_YAML'],
    [GET, 'METHOD_NOT_IN_YAML'],
    ['PUT /widgets', 'YAML_FILE_NOT_FOUND'],
  ]);
});

test('a deleted operation is skipped, but still carrying x-added-in-version is reported', (t) => {
  const result = run(t, { [FILE]: widgetsYaml() }, {
    operations: ops(),
    deletedOperations: { [GET]: { removedIn: '8.8' } },
  });
  assert.equal(result.opSkippedDeleted, 1);
  assert.deepEqual(result.opErrors.map((e) => [e.op, e.issue]), [[GET, 'UNEXPECTED_ANNOTATION_ON_DELETED_OPERATION']]);
});

test('an operation in the YAML but not in the version map is listed as extra, not an error', (t) => {
  const result = run(t, { [FILE]: widgetsYaml() }, { operations: { [POST]: ops()[POST] } }, { [POST]: FILE });
  assert.deepEqual(issues(result), []);
  assert.deepEqual(result.extraOps.map((e) => [e.op, e.hasAnnotation]), [[GET, true]]);
});

// ── Properties ──────────────────────────────────────────────────────────────

test('Rule 1: a property added with its endpoint needs no annotation, and must not have one', (t) => {
  const vm = { operations: ops(), properties: { a: prop('name', '8.6') } };
  assert.deepEqual(issues(run(t, { [FILE]: widgetsYaml() }, vm)), []);

  const annotated = widgetsYaml({ widget: { 'x-properties-added-in-version': [{ propertyName: 'name', addedInVersion: '8.6' }] } });
  const result = run(t, { [FILE]: annotated }, vm);
  assert.deepEqual(result.propErrors.map((e) => [e.issue, e.suppressedBy]), [['UNEXPECTED_ANNOTATION_ON_SUPPRESSED', 'rule1']]);
});

test('a property added after its endpoint must be annotated with its own version', (t) => {
  const vm = { operations: ops(), properties: { a: prop('tenantId', '8.8') } };
  const missing = run(t, { [FILE]: widgetsYaml() }, vm);
  assert.deepEqual(missing.propErrors.map((e) => [e.issue, e.propName, e.expected]), [['MISSING_X_PROPERTIES_ADDED_IN_VERSION', 'tenantId', '8.8']]);

  const wrong = widgetsYaml({ widget: { 'x-properties-added-in-version': [{ propertyName: 'tenantId', addedInVersion: '8.7' }] } });
  assert.deepEqual(run(t, { [FILE]: wrong }, vm).propErrors.map((e) => [e.issue, e.expected, e.actual]), [['VERSION_MISMATCH', '8.8', '8.7']]);

  const right = widgetsYaml({ widget: { 'x-properties-added-in-version': [{ propertyName: 'tenantId', addedInVersion: '8.8' }] } });
  const ok = run(t, { [FILE]: right }, vm);
  assert.deepEqual(issues(ok), []);
  assert.equal(ok.propOk, 1);
});

test('Rule 3: a shared schema needs no annotation when every consumer matches its earliest version', (t) => {
  const vm = { operations: ops('8.6', '8.6'), properties: { a: prop('name', '8.6'), b: prop('name', '8.6', { endpoint: GET }) } };
  const result = run(t, { [FILE]: widgetsYaml() }, vm);
  assert.deepEqual(issues(result), []);
  assert.equal([...result.expected.values()][0].reason, 'rule3');
});

test('Rule 3: a shared schema with consumers from different versions takes the earliest version', (t) => {
  // The later consumer comes first, so the earliest version has to be found, not just kept.
  const vm = { operations: ops('8.6', '8.8'), properties: { b: prop('name', '8.8', { endpoint: GET }), a: prop('name', '8.6') } };
  const result = run(t, { [FILE]: widgetsYaml() }, vm);
  assert.deepEqual(result.propErrors.map((e) => [e.issue, e.expected]), [['MISSING_X_PROPERTIES_ADDED_IN_VERSION', '8.6']]);
});

test('Rule 2: a child added with its parent property needs no annotation of its own', (t) => {
  const yaml = widgetsYaml();
  yaml.components.schemas.Widget.properties.tenantId = { type: 'object', properties: { id: { type: 'string' } } };
  yaml.components.schemas.Widget['x-properties-added-in-version'] = [{ propertyName: 'tenantId', addedInVersion: '8.8' }];
  const child = { ...prop('id', '8.8'), qualifiedName: 'tenantId.id', path: [FILE, 'components', 'schemas', 'Widget', 'properties', 'tenantId', 'properties', 'id'] };
  const vm = { operations: ops(), properties: { parent: prop('tenantId', '8.8', { children: ['child'] }), child } };
  const result = run(t, { [FILE]: yaml }, vm);
  assert.deepEqual(issues(result), []);
  assert.equal(result.expected.get([...result.expected.keys()].find((k) => k.includes('\x00id'))).reason, 'rule2');
});

test('addedInVersionOverride replaces the computed property version', (t) => {
  const yaml = widgetsYaml({ widget: { 'x-properties-added-in-version': [{ propertyName: 'tenantId', addedInVersion: '8.7', addedInVersionOverride: '8.7' }] } });
  const vm = { operations: ops(), properties: { a: prop('tenantId', '8.8') } };
  assert.deepEqual(issues(run(t, { [FILE]: yaml }, vm)), []);
});

test('an annotation the version map does not justify is reported as unknown or orphaned', (t) => {
  const yaml = widgetsYaml({ widget: { 'x-properties-added-in-version': [
    { propertyName: 'name', addedInVersion: '8.7' },
    { propertyName: 'removed', addedInVersion: '8.7' },
  ] } });
  const result = run(t, { [FILE]: yaml }, { operations: ops() });
  assert.deepEqual(result.propErrors.map((e) => [e.issue, e.propName]).sort(), [
    ['ORPHAN_PROPERTY_ANNOTATION', 'removed'],
    ['UNKNOWN_PROPERTY_ANNOTATION', 'name'],
  ]);
});

test('deleted properties and properties no longer in the YAML are not reported', (t) => {
  // A location that no longer resolves is version-map drift the PR author
  // cannot act on: it is counted, not reported.
  const vm = {
    operations: ops(),
    properties: { a: prop('legacy', '8.8'), b: prop('moved', '8.8') },
    deletedProperties: { a: { removedIn: '8.9' } },
  };
  const result = run(t, { [FILE]: widgetsYaml() }, vm);
  assert.deepEqual(issues(result), []);
  assert.equal(result.propMissingTarget, 1);
});
