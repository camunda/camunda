#!/usr/bin/env tsx
import fs from 'fs';
import path from 'path';
import {loadSpec} from '../src/spec/loader.js';
import {generateMissingRequired} from '../src/analysis/missingRequired.js';
import {generateMultipartMissingRequired} from '../src/analysis/multipartMissingRequired.js';
import {generateTypeMismatch} from '../src/analysis/typeMismatch.js';
import {generateUnionViolations} from '../src/analysis/unionViolations.js';
import {generateDeepMissingRequired} from '../src/analysis/deepMissingRequired.js';
import {generateBodyTypeMismatch} from '../src/analysis/bodyTypeMismatch.js';
import {generateConstraintViolations} from '../src/analysis/constraintViolations.js';
import {generateEnumViolations} from '../src/analysis/enumViolations.js';
import {generateAdditionalPropsViolations} from '../src/analysis/additionalProps.js';
import {generateOneOfAmbiguous} from '../src/analysis/oneOfAmbiguous.js';
import {generateOneOfNoneMatch} from '../src/analysis/oneOfNoneMatch.js';
import {generateDiscriminatorMismatch} from '../src/analysis/discriminatorMismatch.js';
import {generateMissingRequiredCombos} from '../src/analysis/missingRequiredCombos.js';
import {
  generateParamMissing,
  generateParamTypeMismatch,
  generateParamEnumViolation,
} from '../src/analysis/parameters.js';
import {generateParamConstraintViolations} from '../src/analysis/paramConstraintViolations.js';
import {
  generateMissingBody,
  generateBodyTopTypeMismatch,
} from '../src/analysis/bodyTopLevel.js';
import {
  generateNestedAdditionalProps,
  generateUniqueItemsViolations,
  generateMultipleOfViolations,
  generateFormatInvalid,
} from '../src/analysis/advancedSchema.js';
import {generateUniversalAdditionalProp} from '../src/analysis/additionalPropUniversal.js';
import {
  generateOneOfMultiAmbiguous,
  generateOneOfCrossBleed,
  generateDiscriminatorStructureMismatch,
} from '../src/analysis/oneOfAdvanced.js';
import {
  generateAllOfMissingRequired,
  generateAllOfConflicts,
} from '../src/analysis/allOf.js';
import {emitQaTests} from '../src/emit/qaEmitter.js';
import {ValidationScenario} from '../src/model/types.js';

interface CliOptions {
  only?: Set<string>;
  outDir: string;
  qaImportDepth: number;
  maxMissing?: number;
  maxTypeMismatch?: number;
  onlyOperations?: Set<string>;
  deep?: boolean; // include deep missing & body type mismatches
}

function parseArgs(): CliOptions {
  const args = process.argv.slice(2);
  // Support both `--flag value` and `--flag=value` syntaxes
  const kv: Record<string, string> = {};
  for (let i = 0; i < args.length; i++) {
    const a = args[i];
    if (a.startsWith('--')) {
      const eq = a.indexOf('=');
      if (eq !== -1) {
        const key = a.slice(0, eq);
        const val = a.slice(eq + 1);
        kv[key] = val;
      } else if (i + 1 < args.length && !args[i + 1].startsWith('--')) {
        kv[a] = args[i + 1];
        i++; // skip value
      } else {
        // boolean style flag (e.g. --deep)
        kv[a] = 'true';
      }
    }
  }
  const get = (k: string) => kv[k];
  const onlyRaw = get('--only');
  const only = onlyRaw
    ? new Set(onlyRaw.split(',').map((s) => s.trim()))
    : undefined;
  const outDir = get('--out-dir') || 'generated';
  const importDepth = get('--qa-import-depth');
  const qaImportDepth = importDepth ? parseInt(importDepth, 10) : 4;
  const maxMissing = get('--max-missing');
  const maxTypeMismatch = get('--max-type-mismatch');
  const onlyOpsRaw = get('--only-operations');
  const onlyOperations = onlyOpsRaw
    ? new Set(onlyOpsRaw.split(',').map((s) => s.trim()))
    : undefined;
  return {
    only,
    outDir,
    qaImportDepth,
    maxMissing: maxMissing ? parseInt(maxMissing, 10) : undefined,
    maxTypeMismatch: maxTypeMismatch
      ? parseInt(maxTypeMismatch, 10)
      : undefined,
    onlyOperations,
    deep:
      Object.prototype.hasOwnProperty.call(kv, '--deep') ||
      args.includes('--deep'),
  };
}

async function main() {
  const opts = parseArgs();
  const specPath = path.resolve(process.cwd(), 'cache', 'rest-api.yaml');
  if (!fs.existsSync(specPath)) {
    console.error('[generate] Spec not found. Run: npm run fetch-spec');
    process.exit(1);
  }
  const model = await loadSpec(specPath);
  let specCommit: string | undefined;
  const commitPath = path.join(path.dirname(specPath), 'spec-commit.txt');
  if (fs.existsSync(commitPath)) {
    try {
      specCommit = (await fs.promises.readFile(commitPath, 'utf8')).trim();
    } catch {
      /* ignore missing commit marker */
    }
  }
  const generationTimestamp = new Date().toISOString();
  const scenarios: ValidationScenario[] = [];
  const kinds =
    opts.only || new Set(['missing-required', 'type-mismatch', 'union']);

  if (kinds.has('missing-required')) {
    scenarios.push(
      ...generateMissingRequired(model.operations, {
        capPerOperation: opts.maxMissing,
        onlyOperations: opts.onlyOperations,
      }),
    );
    scenarios.push(
      ...generateMultipartMissingRequired(model.operations, {
        capPerOperation: opts.maxMissing,
        onlyOperations: opts.onlyOperations,
      }),
    );
    if (opts.deep) {
      scenarios.push(
        ...generateDeepMissingRequired(model.operations, {
          capPerOperation: opts.maxMissing,
          onlyOperations: opts.onlyOperations,
          includeNested: true,
        }),
        ...generateMissingRequiredCombos(model.operations, {
          capPerOperation: opts.maxMissing ? opts.maxMissing * 2 : undefined,
          onlyOperations: opts.onlyOperations,
          maxComboSize: 3,
        }),
      );
    }
  }
  if (kinds.has('type-mismatch')) {
    scenarios.push(
      ...generateTypeMismatch(model.operations, {
        capPerOperation: opts.maxTypeMismatch,
        onlyOperations: opts.onlyOperations,
      }),
    );
    if (opts.deep) {
      scenarios.push(
        ...generateBodyTypeMismatch(model.operations, {
          capPerOperation: opts.maxTypeMismatch,
          onlyOperations: opts.onlyOperations,
          maxPerField: 2,
        }),
      );
    }
  }
  if (kinds.has('union')) {
    scenarios.push(
      ...generateUnionViolations(model.operations, {
        onlyOperations: opts.onlyOperations,
      }),
    );
  }
  if (opts.deep) {
    scenarios.push(
      ...generateConstraintViolations(model.operations, {
        capPerOperation: undefined,
        onlyOperations: opts.onlyOperations,
      }),
      ...generateEnumViolations(model.operations, {
        capPerOperation: undefined,
        onlyOperations: opts.onlyOperations,
      }),
      ...generateAdditionalPropsViolations(model.operations, {
        onlyOperations: opts.onlyOperations,
      }),
      ...generateOneOfAmbiguous(model.operations, {
        capPerOperation: 10,
        onlyOperations: opts.onlyOperations,
      }),
      ...generateOneOfNoneMatch(model.operations, {
        capPerOperation: 1,
        onlyOperations: opts.onlyOperations,
      }),
      ...generateDiscriminatorMismatch(model.operations, {
        onlyOperations: opts.onlyOperations,
      }),
      ...generateParamMissing(model.operations, {
        capPerOperation: 10,
        onlyOperations: opts.onlyOperations,
      }),
      ...generateParamTypeMismatch(model.operations, {
        capPerOperation: 10,
        onlyOperations: opts.onlyOperations,
      }),
      ...generateParamEnumViolation(model.operations, {
        capPerOperation: 10,
        onlyOperations: opts.onlyOperations,
      }),
      ...generateParamConstraintViolations(model.operations, {
        capPerOperation: 10,
        onlyOperations: opts.onlyOperations,
      }),
      ...generateMissingBody(model.operations, {
        onlyOperations: opts.onlyOperations,
      }),
      ...generateBodyTopTypeMismatch(model.operations, {
        onlyOperations: opts.onlyOperations,
      }),
      ...generateNestedAdditionalProps(model.operations, {
        capPerOperation: 5,
        onlyOperations: opts.onlyOperations,
      }),
      ...generateUniqueItemsViolations(model.operations, {
        capPerOperation: 10,
        onlyOperations: opts.onlyOperations,
      }),
      ...generateMultipleOfViolations(model.operations, {
        capPerOperation: 10,
        onlyOperations: opts.onlyOperations,
      }),
      ...generateFormatInvalid(model.operations, {
        capPerOperation: 20,
        onlyOperations: opts.onlyOperations,
      }),
      ...generateUniversalAdditionalProp(model.operations, {
        onlyOperations: opts.onlyOperations,
      }),
      ...generateOneOfMultiAmbiguous(model.operations, {
        onlyOperations: opts.onlyOperations,
        capPerOperation: 20,
      }),
      ...generateOneOfCrossBleed(model.operations, {
        onlyOperations: opts.onlyOperations,
        capPerOperation: 40,
      }),
      ...generateDiscriminatorStructureMismatch(model.operations, {
        onlyOperations: opts.onlyOperations,
      }),
      ...generateAllOfMissingRequired(model.operations, {
        onlyOperations: opts.onlyOperations,
        capPerOperation: 50,
      }),
      ...generateAllOfConflicts(model.operations, {
        onlyOperations: opts.onlyOperations,
        capPerOperation: 50,
      }),
    );
  }

  // Dedupe
  const seen = new Set<string>();
  const deduped: ValidationScenario[] = [];
  for (const s of scenarios) {
    const bodyHash = s.requestBody
      ? fastHash(JSON.stringify(s.requestBody))
      : s.multipartForm
        ? fastHash(JSON.stringify(s.multipartForm))
        : '0';
    const key = [
      s.method,
      s.path,
      s.type,
      s.target || '',
      s.bodyEncoding || 'json',
      bodyHash,
    ].join('|');
    if (seen.has(key)) continue;
    seen.add(key);
    deduped.push(s);
  }

  if (!deduped.length) {
    console.error('[generate] No scenarios produced. Check filters.');
    process.exit(2);
  }

  // ---- Default Multipart Adaptation (pre-emit) ----
  // Behavior: If an operation ONLY declares multipart/form-data (no application/json),
  // convert any JSON-style body scenarios into multipart form submissions and
  // adapt "missing-body" scenarios to an empty multipart submission. This avoids
  // generating tests that would yield 415 due to incorrect media type.
  {
    const adjusted: ValidationScenario[] = [];
    for (const s of deduped) {
      const op = model.operations.find((o) => o.operationId === s.operationId);
      if (!op) {
        adjusted.push(s);
        continue;
      }
      const hasJson = !!op.mediaTypes?.includes('application/json');
      const hasMultipart = !!op.mediaTypes?.includes('multipart/form-data');
      const multipartOnly = hasMultipart && !hasJson;
      if (multipartOnly) {
        const isJsonScenario = !!s.requestBody && !s.bodyEncoding; // default JSON body
        if (isJsonScenario) {
          const form: Record<string, string> = {};
          try {
            for (const [k, v] of Object.entries(
              s.requestBody as Record<string, unknown>,
            )) {
              if (v == null) continue;
              if (
                typeof v === 'string' ||
                typeof v === 'number' ||
                typeof v === 'boolean'
              ) {
                form[k] = String(v);
              } else {
                form[k] = JSON.stringify(v);
              }
            }
          } catch {
            /* ignore */
          }
          adjusted.push({
            ...s,
            bodyEncoding: 'multipart',
            multipartForm: form,
            requestBody: undefined,
          });
          continue;
        }
        if (s.type === 'missing-body' && !s.bodyEncoding) {
          adjusted.push({
            ...s,
            bodyEncoding: 'multipart',
            multipartForm: {},
            requestBody: undefined,
          });
          continue;
        }
      }
      adjusted.push(s);
    }
    // Re-dedupe after adaptation
    const seen2 = new Set<string>();
    const final: ValidationScenario[] = [];
    for (const s of adjusted) {
      const bodyHash = s.requestBody
        ? fastHash(JSON.stringify(s.requestBody))
        : s.multipartForm
          ? fastHash(JSON.stringify(s.multipartForm))
          : '0';
      const key = [
        s.method,
        s.path,
        s.type,
        s.target || '',
        s.bodyEncoding || 'json',
        bodyHash,
      ].join('|');
      if (seen2.has(key)) continue;
      seen2.add(key);
      final.push(s);
    }
    deduped.length = 0;
    deduped.push(...final);
  }

  // ---- Structural Additional-Prop Dedupe (post-adaptation) ----
  // Collapse scenarios of type additional-prop / additional-prop-general that are identical
  // except for synthetic extra property field names (e.g. __unexpectedField vs __extraField).
  // We normalize any top-level property whose name starts with '__' to a sentinel key '__X__'
  // when building a structural signature. This keeps one representative per unique shape.
  {
    // ---- Multipart Param-Only Adaptation ----
    // At this point we've already adapted body-bearing JSON scenarios for multipart-only operations.
    // However, parameter-only scenarios (param-missing, param-type-mismatch, param-enum-violation)
    // may still be emitted with implicit JSON expectations (json headers added downstream) which
    // causes 415 Unsupported Media Type on endpoints that ONLY accept multipart/form-data.
    // We convert those to empty multipart submissions so the server processes the request and
    // returns a 400 (parameter validation) instead of 415 (media type), aligning with intent.
    for (const s of deduped) {
      if (s.requestBody || s.multipartForm) continue; // already has a body representation
      if (
        !(
          s.type === 'param-missing' ||
          s.type === 'param-type-mismatch' ||
          s.type === 'param-enum-violation'
        )
      )
        continue;
      const op = model.operations.find((o) => o.operationId === s.operationId);
      if (!op) continue;
      const hasJson = !!op.mediaTypes?.includes('application/json');
      const hasMultipart = !!op.mediaTypes?.includes('multipart/form-data');
      const multipartOnly = hasMultipart && !hasJson;
      if (!multipartOnly) continue;
      // mutate scenario in-place: treat as empty multipart body
      s.bodyEncoding = 'multipart';
      s.multipartForm = {};
    }

    const structuralSeen = new Set<string>();
    const filtered: ValidationScenario[] = [];
    let removed = 0;
    for (const s of deduped) {
      if (
        s.type !== 'additional-prop' &&
        s.type !== 'additional-prop-general'
      ) {
        filtered.push(s);
        continue;
      }
      // Build structural signature
      let shape = '';
      // Prefer multipartForm if present (multipart scenario) else requestBody.
      const carrier = s.multipartForm || s.requestBody;
      if (carrier && typeof carrier === 'object' && !Array.isArray(carrier)) {
        const entries = Object.entries(carrier as Record<string, unknown>)
          .map(([k, v]) => {
            const normK = k.startsWith('__') ? '__X__' : k; // normalize synthetic key
            const t =
              v === null ? 'null' : Array.isArray(v) ? 'array' : typeof v;
            return [normK, t] as [string, string];
          })
          .sort((a, b) => a[0].localeCompare(b[0]));
        shape = entries.map((e) => e.join(':')).join(',');
      } else {
        shape = 'no-body';
      }
      // Group both additional-prop variants into a single class for dedupe
      const groupType =
        s.type === 'additional-prop' || s.type === 'additional-prop-general'
          ? 'additional-prop*'
          : s.type;
      const sig = [
        s.method,
        s.path,
        groupType,
        s.bodyEncoding || 'json',
        shape,
      ].join('|');
      if (structuralSeen.has(sig)) {
        removed++;
        continue;
      }
      structuralSeen.add(sig);
      filtered.push(s);
    }
    if (removed)
      console.log(
        `[generate] Structural additional-prop dedupe removed ${removed} scenarios`,
      );
    deduped.length = 0;
    deduped.push(...filtered);
  }

  // ---- Structural Multipart Param/Body Type-Mismatch Dedupe ----
  // Collapse multipart param-type-mismatch AND body field type-mismatch scenarios that only
  // differ by the primitive value supplied for the same form field. After adaptation all primitives
  // become strings, so variants (123 vs true vs "abc") are behaviorally identical.
  {
    const seenMultipartMismatch = new Set<string>();
    const filtered: ValidationScenario[] = [];
    let removed = 0;
    for (const s of deduped) {
      const isCandidate =
        s.type === 'param-type-mismatch' || s.type === 'type-mismatch';
      if (!isCandidate || s.bodyEncoding !== 'multipart' || !s.multipartForm) {
        filtered.push(s);
        continue;
      }
      // Build signature ignoring actual values; just the field names present plus target.
      const fieldNames = Object.keys(s.multipartForm).sort().join(',');
      const sig = [
        s.method,
        s.path,
        'any-multipart-mismatch*',
        s.target || '',
        fieldNames,
      ].join('|');
      if (seenMultipartMismatch.has(sig)) {
        removed++;
        continue;
      }
      seenMultipartMismatch.add(sig);
      filtered.push(s);
    }
    if (removed)
      console.log(
        `[generate] Structural multipart mismatch dedupe removed ${removed} scenarios`,
      );
    deduped.length = 0;
    deduped.push(...filtered);
  }

  await emitQaTests(deduped, {
    outDir: opts.outDir,
    qaImportDepth: opts.qaImportDepth,
    specCommit,
    generationTimestamp,
  });
  console.log('[generate] Summary:', {
    totalScenarios: deduped.length,
    kinds: Array.from(new Set(deduped.map((s) => s.type))).sort(),
    deepMode: opts.deep,
    maxMissing: opts.maxMissing ?? null,
    maxTypeMismatch: opts.maxTypeMismatch ?? null,
  });

  const manifest = {
    generatedAt: new Date().toISOString(),
    counts: deduped.reduce<Record<string, number>>((acc, s) => {
      acc[s.type] = (acc[s.type] || 0) + 1;
      return acc;
    }, {}),
    specCommit,
    generationTimestamp,
    total: deduped.length,
    options: {
      deep: opts.deep,
      maxMissing: opts.maxMissing ?? null,
      maxTypeMismatch: opts.maxTypeMismatch ?? null,
      onlyKinds: opts.only ? Array.from(opts.only) : null,
      onlyOperations: opts.onlyOperations
        ? Array.from(opts.onlyOperations)
        : null,
    },
  };
  await fs.promises.writeFile(
    path.join(opts.outDir, 'MANIFEST.json'),
    JSON.stringify(manifest, null, 2),
  );
  // Coverage report per operation & kind
  // Normalize kinds to avoid double counting (treat body-top-type-mismatch as type-mismatch)
  const kindAlias: Record<string, string> = {
    'body-top-type-mismatch': 'type-mismatch',
  };
  const normalizedScenarios = deduped.map((s) => ({
    ...s,
    type: kindAlias[s.type] || s.type,
  }));
  const allKinds = Array.from(
    new Set(normalizedScenarios.map((s) => s.type)),
  ).sort();
  interface OpCoverage {
    operationId: string;
    method: string;
    path: string;
    counts: Record<string, number>;
    total: number;
    kinds: string[];
    missingKinds: string[];
  }
  const byOperation: Record<string, OpCoverage> = {};
  for (const s of normalizedScenarios) {
    const key = s.operationId;
    let oc = byOperation[key];
    if (!oc) {
      oc = {
        operationId: s.operationId,
        method: s.method,
        path: s.path,
        counts: {},
        total: 0,
        kinds: [],
        missingKinds: [],
      };
      byOperation[key] = oc;
    }
    oc.counts[s.type] = (oc.counts[s.type] || 0) + 1;
    oc.total++;
  }
  for (const oc of Object.values(byOperation)) {
    oc.kinds = Object.keys(oc.counts).sort();
    oc.missingKinds = allKinds.filter((k) => !oc.counts[k]);
  }
  const coverage = {
    generatedAt: new Date().toISOString(),
    specCommit,
    totalScenarios: deduped.length,
    scenarioKinds: allKinds,
    generationOptions: {
      deep: opts.deep,
      maxMissing: opts.maxMissing ?? null,
      maxTypeMismatch: opts.maxTypeMismatch ?? null,
      onlyKinds: opts.only ? Array.from(opts.only) : null,
      onlyOperations: opts.onlyOperations
        ? Array.from(opts.onlyOperations)
        : null,
    },
    operations: Object.values(byOperation).sort((a, b) =>
      a.operationId.localeCompare(b.operationId),
    ),
  };
  // ---- Applicability Analysis ----
  interface Applicability {
    applicable: Set<string>;
    present: Set<string>;
    rawPct: number;
    applicablePct: number;
    missingApplicable: string[];
  }
  const opScenarioKinds: Record<string, Set<string>> = {};
  for (const s of normalizedScenarios) {
    (opScenarioKinds[s.operationId] ||= new Set()).add(s.type);
  }
  // Build feature-derived applicability
  // Using loose typing for OpenAPI schema fragments; full typing not required for generation logic.
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  function analyzeBodyFeatures(body: any): Record<string, boolean> {
    const flags = {
      hasObject: false,
      hasEnums: false,
      hasOneOf: false,
      hasDiscriminator: false,
      hasAllOf: false,
      hasUniqueItems: false,
      hasMultipleOf: false,
      hasConstraints: false,
      hasFormats: false,
      hasNestedObject: false,
    };
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const seen = new Set<any>();
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    function walk(node: any, depth: number) {
      if (!node || typeof node !== 'object' || seen.has(node)) return;
      seen.add(node);
      if (Array.isArray(node.oneOf)) flags.hasOneOf = true;
      if (node.discriminator) flags.hasDiscriminator = true;
      if (Array.isArray(node.allOf)) flags.hasAllOf = true;
      if (Array.isArray(node.enum)) flags.hasEnums = true;
      const t = node.type;
      if (t === 'object') {
        flags.hasObject = true;
        if (depth > 0) flags.hasNestedObject = true;
      }
      if (t === 'array' && node.items && node.uniqueItems)
        flags.hasUniqueItems = true;
      if (node.multipleOf !== undefined) flags.hasMultipleOf = true;
      const constraintKeys = [
        'minLength',
        'maxLength',
        'minimum',
        'maximum',
        'exclusiveMinimum',
        'exclusiveMaximum',
        'minItems',
        'maxItems',
        'pattern',
      ];
      if (constraintKeys.some((k) => node[k] !== undefined))
        flags.hasConstraints = true;
      if (node.format) flags.hasFormats = true;
      if (node.properties)
        for (const v of Object.values(node.properties))
          walk(v as any, depth + 1); // eslint-disable-line @typescript-eslint/no-explicit-any
      if (node.items) walk(node.items, depth + 1);
      if (Array.isArray(node.allOf)) for (const p of node.allOf) walk(p, depth);
      if (Array.isArray(node.oneOf)) for (const p of node.oneOf) walk(p, depth);
    }
    walk(body, 0);
    return flags;
  }
  const applicabilityPerOp: Record<string, Applicability> = {};
  for (const op of model.operations) {
    const present = opScenarioKinds[op.operationId] || new Set();
    const applicable = new Set<string>();
    // Parameters applicability
    const requiredParams = op.parameters.filter((p) => p.required);
    if (requiredParams.length) applicable.add('param-missing');
    if (op.parameters.some((p) => p.schema && (p.schema.type || p.schema.enum)))
      applicable.add('param-type-mismatch');
    if (op.parameters.some((p) => Array.isArray(p.schema?.enum)))
      applicable.add('param-enum-violation');
    // Body-based applicability
    const body = op.requestBodySchema || op.multipartSchema;
    if (body) {
      const f = analyzeBodyFeatures(body);
      if (f.hasObject) {
        applicable.add('missing-body');
        // required fields
        const reqList = Array.isArray(body.required)
          ? body.required
          : op.multipartRequiredProps || [];
        if (reqList.length) {
          applicable.add('missing-required');
          if (reqList.length > 1) applicable.add('missing-required-combo');
        }
        applicable.add('type-mismatch');
        applicable.add('body-top-type-mismatch');
        applicable.add('additional-prop-general');
        if (f.hasNestedObject) applicable.add('nested-additional-prop');
      }
      if (f.hasEnums) applicable.add('enum-violation');
      if (f.hasOneOf) {
        applicable.add('union');
        applicable.add('oneof-ambiguous');
        applicable.add('oneof-none-match');
        applicable.add('oneof-multi-ambiguous');
        applicable.add('oneof-cross-bleed');
      }
      if (f.hasDiscriminator) {
        applicable.add('discriminator-mismatch');
        applicable.add('discriminator-structure-mismatch');
      }
      if (f.hasAllOf) {
        applicable.add('allof-missing-required');
        applicable.add('allof-conflict');
      }
      if (f.hasUniqueItems) applicable.add('unique-items-violation');
      if (f.hasMultipleOf) applicable.add('multiple-of-violation');
      if (f.hasConstraints) applicable.add('constraint-violation');
      if (f.hasFormats) applicable.add('format-invalid');
    }
    // Include actually present kinds in applicability to prevent >100%
    for (const pk of present) if (!applicable.has(pk)) applicable.add(pk);
    const rawPct = present.size ? (present.size / allKinds.length) * 100 : 0;
    const applicablePct = applicable.size
      ? Math.min(100, (present.size / applicable.size) * 100)
      : 0;
    const missingApplicable = Array.from(applicable)
      .filter((k) => !present.has(k))
      .sort();
    applicabilityPerOp[op.operationId] = {
      applicable,
      present,
      rawPct,
      applicablePct,
      missingApplicable,
    };
  }
  // Endpoint coverage
  const totalOps = model.operations.length;
  const coveredOps = Object.keys(opScenarioKinds).length;
  const endpointCoveragePct = totalOps ? (coveredOps / totalOps) * 100 : 0;
  // Enhance coverage JSON
  // Cast to mutable to enrich coverage object without creating a new interface hierarchy.
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  (coverage as any).endpointTotals = {
    totalOps,
    coveredOps,
    endpointCoveragePct: Number(endpointCoveragePct.toFixed(1)),
  };
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  (coverage as any).operations = (coverage as any).operations.map((oc: any) => {
    const appl = applicabilityPerOp[oc.operationId];
    return {
      ...oc,
      rawKindCoveragePct: Number(appl.rawPct.toFixed(1)),
      applicableKindCoveragePct: Number(appl.applicablePct.toFixed(1)),
      applicableKindCount: appl.applicable.size,
      presentKindCount: appl.present.size,
      missingApplicableKinds: appl.missingApplicable,
    };
  });
  await fs.promises.writeFile(
    path.join(opts.outDir, 'COVERAGE.json'),
    JSON.stringify(coverage, null, 2),
  );
  // Markdown summary
  const md: string[] = [];
  md.push('# Request Validation Coverage', '');
  md.push(`Generated: ${coverage.generatedAt}`);
  md.push(`Spec Commit: ${specCommit}`, '');
  md.push(`Total scenarios: ${coverage.totalScenarios}`, '');
  const totalKinds = allKinds.length || 1;
  // Precompute percentages
  const percents: Record<string, string> = {};
  let sumPct = 0;
  let fullCount = 0;
  for (const oc of coverage.operations) {
    const pct = (oc.kinds.length / totalKinds) * 100;
    if (pct === 100) fullCount++;
    sumPct += pct;
    percents[oc.operationId] = pct.toFixed(0) + '%';
  }
  const avgPct = (sumPct / coverage.operations.length).toFixed(1);
  md.push(`Scenario kinds generated this run: ${totalKinds}`);
  md.push(`Average kind coverage per operation: ${avgPct}%`);
  md.push(
    `Operations with full kind coverage: ${fullCount}/${coverage.operations.length}`,
    '',
  );
  md.push(
    'Kind coverage % = (# kinds present for operation / total scenario kinds this run) * 100.',
  );
  md.push('');
  const header = [
    'OperationId',
    'Method',
    'Path',
    'Total',
    'KindCov%',
    'AppKindCov%',
    'ApplicableKinds',
    'PresentKinds',
    ...allKinds,
  ];
  let avgApplicablePct = 0;
  let opsWithApplicable = 0;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  for (const oc of (coverage as any).operations) {
    if (oc.applicableKindCount) {
      avgApplicablePct += oc.applicableKindCoveragePct;
      opsWithApplicable++;
    }
  }
  const avgAppPctStr = opsWithApplicable
    ? (avgApplicablePct / opsWithApplicable).toFixed(1)
    : '0.0';
  md.push(
    `Average applicable kind coverage (ops with applicability): ${avgAppPctStr}%`,
    '',
  );
  md.push('| ' + header.join(' | ') + ' |');
  md.push('| ' + header.map(() => '---').join(' | ') + ' |');
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  for (const oc of (coverage as any).operations) {
    const row = [
      oc.operationId,
      oc.method,
      oc.path,
      String(oc.total),
      percents[oc.operationId],
      oc.applicableKindCoveragePct + '%',
      String(oc.applicableKindCount),
      String(oc.presentKindCount),
      ...allKinds.map((k) => String(oc.counts[k] || '')),
    ];
    md.push('| ' + row.join(' | ') + ' |');
  }
  md.push('', 'Missing kinds per operation:');
  for (const oc of coverage.operations) {
    if (oc.missingKinds.length)
      md.push(`- ${oc.operationId}: ${oc.missingKinds.join(', ')}`);
  }
  md.push(
    '',
    `Endpoint coverage: ${coveredOps}/${totalOps} (${endpointCoveragePct.toFixed(1)}%) have at least one scenario.`,
  );
  // --- True Gaps Summary Matrix ---
  md.push('', 'True Gaps Summary (applicable missing kinds aggregated):');
  interface KindGapStats {
    applicableOps: number;
    missingOps: number;
    sampleMissing: string[];
  }
  const kindStats: Record<string, KindGapStats> = {};
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  for (const oc of (coverage as any).operations) {
    const appl: string[] = Array.from(
      applicabilityPerOp[oc.operationId].applicable,
    );
    const missing: Set<string> = new Set(
      applicabilityPerOp[oc.operationId].missingApplicable,
    );
    for (const k of appl) {
      const stat = (kindStats[k] ||= {
        applicableOps: 0,
        missingOps: 0,
        sampleMissing: [],
      });
      stat.applicableOps++;
      if (missing.has(k)) {
        stat.missingOps++;
        if (stat.sampleMissing.length < 5)
          stat.sampleMissing.push(oc.operationId);
      }
    }
  }
  const kindRows = Object.entries(kindStats)
    .map(([k, s]) => ({
      kind: k,
      applicableOps: s.applicableOps,
      missingOps: s.missingOps,
      missingPct: s.applicableOps ? (s.missingOps / s.applicableOps) * 100 : 0,
      sample: s.sampleMissing.join(', '),
    }))
    .sort(
      (a, b) => b.missingOps - a.missingOps || a.kind.localeCompare(b.kind),
    );
  if (kindRows.length) {
    md.push(
      '| Kind | MissingOps | ApplicableOps | Missing% | SampleMissingOps |',
    );
    md.push('| --- | --- | --- | --- | --- |');
    for (const r of kindRows) {
      md.push(
        `| ${r.kind} | ${r.missingOps} | ${r.applicableOps} | ${r.missingPct.toFixed(1)}% | ${r.sample} |`,
      );
    }
  } else {
    md.push('- None');
  }
  // Collapsible full per-operation detail
  md.push('', '<details><summary>Full per-operation True Gaps list</summary>');
  let anyFull = false;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  for (const oc of (coverage as any).operations) {
    const missingApp: string[] =
      applicabilityPerOp[oc.operationId].missingApplicable;
    if (missingApp.length) {
      md.push(`- ${oc.operationId}: ${missingApp.join(', ')}`);
      anyFull = true;
    }
  }
  if (!anyFull) md.push('- None');
  md.push('</details>');
  md.push(
    '',
    'Applicable missing kinds are structurally possible for that operation and should be prioritized.',
  );
  // --- Formatting Normalization (Spotless-friendly) ---
  function normalizeMarkdown(lines: string[]): string[] {
    const out: string[] = [];
    let blankStreak = 0;
    for (const raw of lines) {
      // Trim trailing whitespace (Spotless will do this; we preempt churn)
      const line = raw.replace(/[ \t]+$/g, '');
      if (line.trim() === '') {
        blankStreak++;
        if (blankStreak > 1) continue; // collapse multiple blank lines
        out.push('');
        continue;
      }
      blankStreak = 0;
      out.push(line);
    }
    // Normalize tables: ensure single space padding around cell separators
    const norm: string[] = [];
    for (let i = 0; i < out.length; i++) {
      const l = out[i];
      // Detect a markdown table header row followed by a separator row
      if (
        /^\|.+\|$/.test(l) &&
        i + 1 < out.length &&
        /^\|[ \-:|]+\|$/.test(out[i + 1])
      ) {
        // Collect table block until a blank line or non-row
        const tableLines = [l];
        let j = i + 1;
        while (
          j < out.length &&
          /^\|.*\|$/.test(out[j]) &&
          out[j].trim() !== ''
        ) {
          tableLines.push(out[j]);
          j++;
        }
        // Normalize each table line
        const rebuilt = tableLines.map((tl, idx) => {
          const cells = tl
            .split('|')
            .slice(1, -1)
            .map((c) => c.trim());
          if (idx === 1) {
            // separator line: rebuild with '---'
            return '| ' + cells.map(() => '---').join(' | ') + ' |';
          }
          return '| ' + cells.join(' | ') + ' |';
        });
        norm.push(...rebuilt);
        i = i + tableLines.length - 1;
        continue;
      }
      norm.push(l);
    }
    return norm;
  }
  const normalized = normalizeMarkdown(md);
  let finalText = normalized.join('\n');
  if (!finalText.endsWith('\n')) finalText += '\n'; // ensure trailing newline
  await fs.promises.writeFile(path.join(opts.outDir, 'COVERAGE.md'), finalText);
  console.log('[generate] Wrote manifest and', scenarios.length, 'scenarios');
  console.log('[generate] After dedupe:', deduped.length, 'scenarios');
  console.log('[generate] Coverage files written: COVERAGE.json, COVERAGE.md');
}

main().catch((e) => {
  console.error('[generate] FAILED', e);
  process.exit(1);
});

function fastHash(str: string): string {
  let h = 0,
    i = 0;
  const len = str.length;
  while (i < len) {
    h = (h * 31 + str.charCodeAt(i++)) | 0;
  }
  return (h >>> 0).toString(36);
}
