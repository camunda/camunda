#!/usr/bin/env node
/**
 * Render the C8 REST OpenAPI spec as a line-oriented API-surface snapshot.
 *
 * The snapshot (zeebe/gateway-protocol/rest-api-surface.txt) is committed next
 * to the spec. Every spec change that alters what API consumers or SDK code
 * generators see therefore shows up as a small, reviewable diff in the PR
 * that causes it. Descriptions and examples are ignored, so documentation
 * edits don't touch it.
 *
 * One line per operation, schema and (allOf-flattened) property:
 *
 *   OP     POST /process-instances  createProcessInstance  params=[] req=application/json:ProcessInstanceCreationInstruction  200=CreateProcessInstanceResult ...
 *   SCHEMA ProcessInstanceCreationInstructionById  object allOf=[ProcessInstanceCreationInstructionBase]
 *   PROP   ProcessInstanceCreationInstructionById.tenantId  TenantId  optional  semantic=TenantId
 *   PROP   ProcessInstanceCreationInstructionById.variables  object(inline@ProcessInstanceCreationInstructionBase.variables)  optional
 *
 * What it records, and why:
 *   - wire contract: operations, parameters, request/response schemas, response
 *     codes, property types, required, nullable, enum values, oneOf variants,
 *     closed objects (additionalProperties: false), `not` constraints;
 *   - generated-code contract: component schema names, allOf structure,
 *     semantic types (x-semantic-type), and which schema owns an inline object
 *     (generators name inline classes after their owner, e.g. `…ByKeyVariables`).
 *
 * Properties are flattened through allOf, so moving fields into a shared base
 * schema changes only the SCHEMA lines (and inline-object owners), not every
 * property line.
 *
 * Usage (from this directory, after `npm ci`):
 *   npm run generate   # rewrite the committed snapshot
 *   npm run check      # fail if the committed snapshot is out of date (CI)
 *   node api-surface.mjs --spec bundled.json   # print the surface of a bundled spec
 */
import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import { dirname, resolve, relative } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';
import { bundle } from 'camunda-schema-bundler';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(here, '../../..');
const specDir = resolve(repoRoot, process.env.OCA_SPEC_PATH ?? 'zeebe/gateway-protocol/src/main/proto/v2');
const snapshotPath = resolve(repoRoot, process.env.API_SURFACE_PATH ?? 'zeebe/gateway-protocol/rest-api-surface.txt');

const HEADER = [
  '# C8 REST API surface. GENERATED - do not edit by hand.',
  '# Regenerate: cd .github/scripts/api-surface && npm ci && npm run generate',
  '# Reviewers: every changed line here is a change to the public API contract or to',
  '# generated SDK code. See .github/scripts/api-surface/README.md for how to classify it.',
];

// ---------------------------------------------------------------------------
// Rendering
// ---------------------------------------------------------------------------

const refName = (ref) => ref.slice(ref.lastIndexOf('/') + 1);
const isObj = (v) => v !== null && typeof v === 'object' && !Array.isArray(v);
const byKey = (a, b) => (a < b ? -1 : a > b ? 1 : 0);
const sortedEntries = (o) => Object.entries(o ?? {}).sort(([a], [b]) => byKey(a, b));

/** Deterministic, compact JSON (sorted keys) for `not` constraints. */
function stableJson(v) {
  if (Array.isArray(v)) return `[${v.map(stableJson).join(', ')}]`;
  if (isObj(v)) return `{${sortedEntries(v).map(([k, x]) => `${JSON.stringify(k)}: ${stableJson(x)}`).join(', ')}}`;
  return JSON.stringify(v);
}

export function renderSurface(spec) {
  const schemas = spec.components?.schemas ?? {};

  /** Single-$ref allOf wrappers (used to attach nullable/description to a $ref) count as the ref. */
  const singleRef = (s) =>
    '$ref' in s ? refName(s.$ref)
      : Array.isArray(s.allOf) && s.allOf.length === 1 && '$ref' in s.allOf[0] ? refName(s.allOf[0].$ref)
        : null;

  function typeOf(s, owner, prop) {
    if (!isObj(s)) return '?';
    const ref = singleRef(s);
    if (ref) return ref;
    for (const comb of ['oneOf', 'anyOf']) {
      if (comb in s) return `${comb}(${s[comb].map((x) => typeOf(x)).join(' | ')})`;
    }
    if ('allOf' in s) return `allOf(${s.allOf.map((x) => typeOf(x)).join(' & ')})`;
    const t = s.type;
    if (t === 'array') return `${typeOf(s.items ?? {}, owner, prop)}[]`;
    if ('enum' in s) return `enum<${t}>(${s.enum.map(String).join(',')})`;
    if (t === 'object' || 'properties' in s || 'additionalProperties' in s) {
      const ap = s.additionalProperties;
      if (s.properties && Object.keys(s.properties).length) return `object(inline@${owner}.${prop})`;
      if (isObj(ap)) return `map<${typeOf(ap)}>`;
      return owner ? `object(inline@${owner}.${prop})` : 'object';
    }
    return s.format ? `${t}:${s.format}` : (t ?? 'any');
  }

  function semanticOf(s) {
    if (!isObj(s)) return null;
    if (s['x-semantic-type']) return s['x-semantic-type'];
    const target = singleRef(s);
    return target && isObj(schemas[target]) ? (schemas[target]['x-semantic-type'] ?? null) : null;
  }

  /** {prop: {schema, required, owner}}, including allOf-inherited properties. */
  function collect(name, seen = new Set()) {
    if (seen.has(name) || !(name in schemas)) return {};
    seen.add(name);
    const s = schemas[name];
    const out = {};
    for (const part of s.allOf ?? []) {
      if ('$ref' in part) Object.assign(out, collect(refName(part.$ref), seen));
      else {
        const req = new Set(part.required ?? []);
        for (const [p, ps] of Object.entries(part.properties ?? {})) out[p] = { schema: ps, required: req.has(p), owner: name };
      }
    }
    const req = new Set(s.required ?? []);
    for (const [p, ps] of Object.entries(s.properties ?? {})) out[p] = { schema: ps, required: req.has(p), owner: name };
    // `required` may be declared on the child for inherited properties.
    for (const p of req) if (p in out) out[p] = { ...out[p], required: true };
    return out;
  }

  const lines = [];

  for (const [path, item] of sortedEntries(spec.paths)) {
    for (const [method, op] of sortedEntries(item)) {
      if (!isObj(op) || method.startsWith('x-') || method === 'parameters') continue;
      const params = (op.parameters ?? []).map(
        (p) => `${p.in}:${p.name}${p.required ? '!' : ''}=${typeOf(p.schema ?? {})}`,
      );
      let req = '';
      if (op.requestBody) {
        const ct = sortedEntries(op.requestBody.content);
        req = ' req=' + ct.map(([c, v]) => `${c}:${typeOf(v.schema ?? {}, op.operationId, 'body')}`).join(',');
        if (!op.requestBody.required) req += '(optional)';
      }
      const resps = sortedEntries(op.responses).map(([code, r]) => {
        const bodies = [...new Set(Object.values(r.content ?? {}).map((v) => typeOf(v.schema ?? {}, op.operationId, code)))].sort(byKey);
        return `${code}=${bodies.join(',') || '-'}`;
      });
      lines.push(`OP     ${method.toUpperCase()} ${path}  ${op.operationId}  params=[${params.join(', ')}]${req}  ${resps.join(' ')}`);
    }
  }

  for (const [name, s] of sortedEntries(schemas)) {
    if (!isObj(s)) continue;
    const bits = [];
    if ('oneOf' in s || 'anyOf' in s) {
      const comb = 'oneOf' in s ? 'oneOf' : 'anyOf';
      bits.push(`${comb}[${s[comb].map((x, i) => typeOf(x, name, `${comb}${i}`)).join(', ')}]`);
      if (s.discriminator) bits.push(`discriminator=${s.discriminator.propertyName}`);
    }
    const parents = (s.allOf ?? []).filter((p) => '$ref' in p).map((p) => refName(p.$ref));
    if ('enum' in s) bits.push(`enum<${s.type}>(${s.enum.map(String).join(',')})`);
    else if (s.type === 'object' || 'properties' in s || parents.length) {
      bits.push('object');
      if (parents.length) bits.push(`allOf=[${parents.join(', ')}]`);
      if (s.additionalProperties === false) bits.push('closed');
    } else if ('type' in s) bits.push(typeOf(s));
    if (s['x-semantic-type']) bits.push(`semantic=${s['x-semantic-type']}`);
    if (s.nullable) bits.push('nullable');
    if (s.not) bits.push(`not=${stableJson(s.not)}`);
    if (s.deprecated) bits.push('deprecated');
    lines.push(`SCHEMA ${name}  ${bits.join(' ')}`);

    for (const [p, { schema: ps, required, owner }] of sortedEntries(collect(name))) {
      const attrs = [typeOf(ps, owner, p), required ? 'required' : 'optional'];
      if (isObj(ps) && ps.nullable) attrs.push('nullable');
      const sem = semanticOf(ps);
      if (sem) attrs.push(`semantic=${sem}`);
      if (isObj(ps) && ps.deprecated) attrs.push('deprecated');
      lines.push(`PROP   ${name}.${p}  ${attrs.join('  ')}`);
    }
  }

  return lines.join('\n') + '\n';
}

// ---------------------------------------------------------------------------
// CLI
// ---------------------------------------------------------------------------

async function bundledSpec() {
  // Same bundling (and options) the SDKs use, so the snapshot sees the
  // component names their generators see.
  const { spec } = await bundle({ specDir, dereferencePathLocalRefs: true });
  return spec;
}

async function main() {
  const args = process.argv.slice(2);
  const specArg = args.indexOf('--spec');
  if (specArg !== -1) {
    process.stdout.write(renderSurface(JSON.parse(readFileSync(args[specArg + 1], 'utf8'))));
    return;
  }

  const surface = HEADER.join('\n') + '\n' + renderSurface(await bundledSpec());
  const rel = relative(repoRoot, snapshotPath);

  if (args.includes('--write')) {
    writeFileSync(snapshotPath, surface);
    console.log(`Wrote ${rel}`);
    return;
  }

  if (args.includes('--check')) {
    const committed = existsSync(snapshotPath) ? readFileSync(snapshotPath, 'utf8') : '';
    if (committed === surface) {
      console.log(`${rel} is up to date.`);
      return;
    }
    const fresh = resolve(here, 'rest-api-surface.generated.txt');
    writeFileSync(fresh, surface);
    const diff = spawnSync('git', ['diff', '--no-index', '--no-color', '--', snapshotPath, fresh], { encoding: 'utf8' });
    console.log(diff.stdout);
    console.log(
      `::error file=${rel}::The C8 REST API surface changed but ${rel} was not updated. ` +
        'Run `npm ci && npm run generate` in .github/scripts/api-surface, review the diff above, and commit the snapshot.',
    );
    process.exitCode = 1;
    return;
  }

  process.stdout.write(surface);
}

await main();
