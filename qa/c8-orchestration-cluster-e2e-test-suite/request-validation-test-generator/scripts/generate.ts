#!/usr/bin/env tsx
import fs from 'fs';
import path from 'path';
import { loadSpec } from '../src/spec/loader.js';
import { generateMissingRequired } from '../src/analysis/missingRequired.js';
import { generateTypeMismatch } from '../src/analysis/typeMismatch.js';
import { generateUnionViolations } from '../src/analysis/unionViolations.js';
import { generateDeepMissingRequired } from '../src/analysis/deepMissingRequired.js';
import { generateBodyTypeMismatch } from '../src/analysis/bodyTypeMismatch.js';
import { generateConstraintViolations } from '../src/analysis/constraintViolations.js';
import { generateEnumViolations } from '../src/analysis/enumViolations.js';
import { generateAdditionalPropsViolations } from '../src/analysis/additionalProps.js';
import { generateOneOfAmbiguous } from '../src/analysis/oneOfAmbiguous.js';
import { generateOneOfNoneMatch } from '../src/analysis/oneOfNoneMatch.js';
import { generateDiscriminatorMismatch } from '../src/analysis/discriminatorMismatch.js';
import { generateMissingRequiredCombos } from '../src/analysis/missingRequiredCombos.js';
import { generateParamMissing, generateParamTypeMismatch, generateParamEnumViolation } from '../src/analysis/parameters.js';
import { generateMissingBody, generateBodyTopTypeMismatch } from '../src/analysis/bodyTopLevel.js';
import { generateNestedAdditionalProps, generateUniqueItemsViolations, generateMultipleOfViolations, generateFormatInvalid } from '../src/analysis/advancedSchema.js';
import { generateUniversalAdditionalProp } from '../src/analysis/additionalPropUniversal.js';
import { generateOneOfMultiAmbiguous, generateOneOfCrossBleed, generateDiscriminatorStructureMismatch } from '../src/analysis/oneOfAdvanced.js';
import { generateAllOfMissingRequired, generateAllOfConflicts } from '../src/analysis/allOf.js';
import { emitQaTests } from '../src/emit/qaEmitter.js';
import { ValidationScenario } from '../src/model/types.js';

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
  const kv: Record<string,string> = {};
  for (let i=0; i<args.length; i++) {
    const a = args[i];
    if (a.startsWith('--')) {
      const eq = a.indexOf('=');
      if (eq !== -1) {
        const key = a.slice(0, eq);
        const val = a.slice(eq+1);
        kv[key] = val;
      } else if (i+1 < args.length && !args[i+1].startsWith('--')) {
        kv[a] = args[i+1];
        i++; // skip value
      } else {
        // boolean style flag (e.g. --deep)
        kv[a] = 'true';
      }
    }
  }
  const get = (k: string) => kv[k];
  const onlyRaw = get('--only');
  const only = onlyRaw ? new Set(onlyRaw.split(',').map((s) => s.trim())) : undefined;
  const outDir = get('--out-dir') || 'generated';
  const importDepth = get('--qa-import-depth');
  const qaImportDepth = importDepth ? parseInt(importDepth, 10) : 4;
  const maxMissing = get('--max-missing');
  const maxTypeMismatch = get('--max-type-mismatch');
  const onlyOpsRaw = get('--only-operations');
  const onlyOperations = onlyOpsRaw ? new Set(onlyOpsRaw.split(',').map((s) => s.trim())) : undefined;
  return {
    only,
    outDir,
    qaImportDepth,
    maxMissing: maxMissing ? parseInt(maxMissing, 10) : undefined,
    maxTypeMismatch: maxTypeMismatch ? parseInt(maxTypeMismatch, 10) : undefined,
    onlyOperations,
    deep: Object.prototype.hasOwnProperty.call(kv,'--deep') || args.includes('--deep'),
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
    try { specCommit = (await fs.promises.readFile(commitPath, 'utf8')).trim(); } catch {}
  }
  const generationTimestamp = new Date().toISOString();
  const scenarios: ValidationScenario[] = [];
  const kinds = opts.only || new Set(['missing-required', 'type-mismatch', 'union']);

  if (kinds.has('missing-required')) {
    scenarios.push(
      ...generateMissingRequired(model.operations, {
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
      ...generateConstraintViolations(model.operations, { capPerOperation: undefined, onlyOperations: opts.onlyOperations }),
      ...generateEnumViolations(model.operations, { capPerOperation: undefined, onlyOperations: opts.onlyOperations }),
      ...generateAdditionalPropsViolations(model.operations, { onlyOperations: opts.onlyOperations }),
      ...generateOneOfAmbiguous(model.operations, { capPerOperation: 10, onlyOperations: opts.onlyOperations }),
      ...generateOneOfNoneMatch(model.operations, { capPerOperation: 1, onlyOperations: opts.onlyOperations }),
      ...generateDiscriminatorMismatch(model.operations, { onlyOperations: opts.onlyOperations }),
      ...generateParamMissing(model.operations, { capPerOperation: 10, onlyOperations: opts.onlyOperations }),
      ...generateParamTypeMismatch(model.operations, { capPerOperation: 10, onlyOperations: opts.onlyOperations }),
      ...generateParamEnumViolation(model.operations, { capPerOperation: 10, onlyOperations: opts.onlyOperations }),
      ...generateMissingBody(model.operations, { onlyOperations: opts.onlyOperations }),
      ...generateBodyTopTypeMismatch(model.operations, { onlyOperations: opts.onlyOperations }),
      ...generateNestedAdditionalProps(model.operations, { capPerOperation: 5, onlyOperations: opts.onlyOperations }),
      ...generateUniqueItemsViolations(model.operations, { capPerOperation: 10, onlyOperations: opts.onlyOperations }),
      ...generateMultipleOfViolations(model.operations, { capPerOperation: 10, onlyOperations: opts.onlyOperations }),
      ...generateFormatInvalid(model.operations, { capPerOperation: 20, onlyOperations: opts.onlyOperations }),
      ...generateUniversalAdditionalProp(model.operations, { onlyOperations: opts.onlyOperations }),
      ...generateOneOfMultiAmbiguous(model.operations, { onlyOperations: opts.onlyOperations, capPerOperation: 20 }),
      ...generateOneOfCrossBleed(model.operations, { onlyOperations: opts.onlyOperations, capPerOperation: 40 }),
      ...generateDiscriminatorStructureMismatch(model.operations, { onlyOperations: opts.onlyOperations }),
      ...generateAllOfMissingRequired(model.operations, { onlyOperations: opts.onlyOperations, capPerOperation: 50 }),
      ...generateAllOfConflicts(model.operations, { onlyOperations: opts.onlyOperations, capPerOperation: 50 }),
    );
  }

  // Dedupe
  const seen = new Set<string>();
  const deduped: ValidationScenario[] = [];
  for (const s of scenarios) {
    const bodyHash = s.requestBody ? fastHash(JSON.stringify(s.requestBody)) : '0';
    const key = [s.method, s.path, s.type, s.target || '', bodyHash].join('|');
    if (seen.has(key)) continue;
    seen.add(key);
    deduped.push(s);
  }

  if (!deduped.length) {
    console.error('[generate] No scenarios produced. Check filters.');
    process.exit(2);
  }

  await emitQaTests(deduped, { outDir: opts.outDir, qaImportDepth: opts.qaImportDepth, specCommit, generationTimestamp });
  console.log('[generate] Summary:', {
    totalScenarios: deduped.length,
    kinds: Array.from(new Set(deduped.map(s=>s.type))).sort(),
    deepMode: opts.deep,
    maxMissing: opts.maxMissing ?? null,
    maxTypeMismatch: opts.maxTypeMismatch ?? null,
  });

  const manifest = {
    generatedAt: new Date().toISOString(),
    counts: deduped.reduce<Record<string, number>>((acc, s) => { acc[s.type] = (acc[s.type]||0)+1; return acc; }, {}),
    specCommit,
    generationTimestamp,
    total: deduped.length,
    options: {
      deep: opts.deep,
      maxMissing: opts.maxMissing ?? null,
      maxTypeMismatch: opts.maxTypeMismatch ?? null,
      onlyKinds: opts.only ? Array.from(opts.only) : null,
      onlyOperations: opts.onlyOperations ? Array.from(opts.onlyOperations) : null,
    },
  };
  await fs.promises.writeFile(path.join(opts.outDir, 'MANIFEST.json'), JSON.stringify(manifest, null, 2));
  // Coverage report per operation & kind
  // Normalize kinds to avoid double counting (treat body-top-type-mismatch as type-mismatch)
  const kindAlias: Record<string,string> = { 'body-top-type-mismatch': 'type-mismatch' };
  const normalizedScenarios = deduped.map(s => ({ ...s, type: kindAlias[s.type] || s.type }));
  const allKinds = Array.from(new Set(normalizedScenarios.map(s => s.type))).sort();
  interface OpCoverage { operationId: string; method: string; path: string; counts: Record<string, number>; total: number; kinds: string[]; missingKinds: string[]; }
  const byOperation: Record<string, OpCoverage> = {};
  for (const s of normalizedScenarios) {
    const key = s.operationId;
    let oc = byOperation[key];
    if (!oc) {
      oc = { operationId: s.operationId, method: s.method, path: s.path, counts: {}, total: 0, kinds: [], missingKinds: [] };
      byOperation[key] = oc;
    }
    oc.counts[s.type] = (oc.counts[s.type] || 0) + 1;
    oc.total++;
  }
  for (const oc of Object.values(byOperation)) {
    oc.kinds = Object.keys(oc.counts).sort();
    oc.missingKinds = allKinds.filter(k => !oc.counts[k]);
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
      onlyOperations: opts.onlyOperations ? Array.from(opts.onlyOperations) : null,
    },
    operations: Object.values(byOperation).sort((a,b)=> a.operationId.localeCompare(b.operationId)),
  };
  // ---- Applicability Analysis ----
  interface Applicability { applicable: Set<string>; present: Set<string>; rawPct: number; applicablePct: number; missingApplicable: string[]; }
  const opScenarioKinds: Record<string, Set<string>> = {};
  for (const s of normalizedScenarios) {
    (opScenarioKinds[s.operationId] ||= new Set()).add(s.type);
  }
  // Build feature-derived applicability
  function analyzeBodyFeatures(body: any): Record<string, boolean> {
    const flags = { hasObject:false, hasEnums:false, hasOneOf:false, hasDiscriminator:false, hasAllOf:false, hasUniqueItems:false, hasMultipleOf:false, hasConstraints:false, hasFormats:false, hasNestedObject:false };
    const seen = new Set<any>();
    function walk(node:any, depth:number){
      if(!node || typeof node!=='object' || seen.has(node)) return; seen.add(node);
      if(Array.isArray(node.oneOf)) flags.hasOneOf = true;
      if(node.discriminator) flags.hasDiscriminator = true;
      if(Array.isArray(node.allOf)) flags.hasAllOf = true;
      if(Array.isArray(node.enum)) flags.hasEnums = true;
      const t=node.type;
      if(t==='object') { flags.hasObject = true; if (depth>0) flags.hasNestedObject = true; }
      if(t==='array' && node.items && node.uniqueItems) flags.hasUniqueItems = true;
      if(node.multipleOf !== undefined) flags.hasMultipleOf = true;
      const constraintKeys=['minLength','maxLength','minimum','maximum','exclusiveMinimum','exclusiveMaximum','minItems','maxItems','pattern'];
      if(constraintKeys.some(k=> node[k] !== undefined)) flags.hasConstraints = true;
      if(node.format) flags.hasFormats = true;
      if(node.properties) for (const v of Object.values<any>(node.properties)) walk(v, depth+1);
      if(node.items) walk(node.items, depth+1);
      if(Array.isArray(node.allOf)) for (const p of node.allOf) walk(p, depth);
      if(Array.isArray(node.oneOf)) for (const p of node.oneOf) walk(p, depth);
    }
    walk(body,0);
    return flags;
  }
  const applicabilityPerOp: Record<string, Applicability> = {};
  for (const op of model.operations) {
    const present = opScenarioKinds[op.operationId] || new Set();
    const applicable = new Set<string>();
    // Parameters applicability
    const requiredParams = op.parameters.filter(p=> p.required);
    if (requiredParams.length) applicable.add('param-missing');
    if (op.parameters.some(p=> p.schema && (p.schema.type || p.schema.enum))) applicable.add('param-type-mismatch');
    if (op.parameters.some(p=> Array.isArray(p.schema?.enum))) applicable.add('param-enum-violation');
    // Body-based applicability
    const body = op.requestBodySchema;
    if (body) {
      const f = analyzeBodyFeatures(body);
      if (f.hasObject) {
        applicable.add('missing-body');
        // required fields
        if (Array.isArray(body.required) && body.required.length) {
          applicable.add('missing-required');
          if (body.required.length > 1) applicable.add('missing-required-combo');
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
    const rawPct = present.size ? (present.size / allKinds.length)*100 : 0;
    const applicablePct = applicable.size ? Math.min(100, (present.size / applicable.size)*100) : 0;
    const missingApplicable = Array.from(applicable).filter(k=> !present.has(k)).sort();
    applicabilityPerOp[op.operationId] = { applicable, present, rawPct, applicablePct, missingApplicable };
  }
  // Endpoint coverage
  const totalOps = model.operations.length;
  const coveredOps = Object.keys(opScenarioKinds).length;
  const endpointCoveragePct = totalOps ? (coveredOps/totalOps*100) : 0;
  // Enhance coverage JSON
  (coverage as any).endpointTotals = { totalOps, coveredOps, endpointCoveragePct: Number(endpointCoveragePct.toFixed(1)) };
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
  await fs.promises.writeFile(path.join(opts.outDir, 'COVERAGE.json'), JSON.stringify(coverage, null, 2));
  // Markdown summary
  const md: string[] = [];
  md.push('# Request Validation Coverage','');
  md.push(`Generated: ${coverage.generatedAt}`);
  md.push(`Spec Commit: ${specCommit}`,'');
  md.push(`Total scenarios: ${coverage.totalScenarios}`,'');
  const totalKinds = allKinds.length || 1;
  // Precompute percentages
  const percents: Record<string,string> = {};
  let sumPct = 0; let fullCount = 0;
  for (const oc of coverage.operations) {
    const pct = (oc.kinds.length / totalKinds) * 100;
    if (pct === 100) fullCount++;
    sumPct += pct;
    percents[oc.operationId] = pct.toFixed(0) + '%';
  }
  const avgPct = (sumPct / coverage.operations.length).toFixed(1);
  md.push(`Scenario kinds generated this run: ${totalKinds}`);
  md.push(`Average kind coverage per operation: ${avgPct}%`);
  md.push(`Operations with full kind coverage: ${fullCount}/${coverage.operations.length}`,'');
  md.push('Kind coverage % = (# kinds present for operation / total scenario kinds this run) * 100.');
  md.push('');
  const header = ['OperationId','Method','Path','Total','KindCov%','AppKindCov%','ApplicableKinds','PresentKinds', ...allKinds];
  let avgApplicablePct = 0;
  let opsWithApplicable = 0;
  for (const oc of (coverage as any).operations) {
    if (oc.applicableKindCount) { avgApplicablePct += oc.applicableKindCoveragePct; opsWithApplicable++; }
  }
  const avgAppPctStr = opsWithApplicable ? (avgApplicablePct/opsWithApplicable).toFixed(1) : '0.0';
  md.push(`Average applicable kind coverage (ops with applicability): ${avgAppPctStr}%`,'');
  md.push('| ' + header.join(' | ') + ' |');
  md.push('| ' + header.map(()=> '---').join(' | ') + ' |');
  for (const oc of (coverage as any).operations) {
    const row = [
      oc.operationId,
      oc.method,
      oc.path,
      String(oc.total),
      percents[oc.operationId],
      oc.applicableKindCoveragePct+'%',
      String(oc.applicableKindCount),
      String(oc.presentKindCount),
      ...allKinds.map(k => String(oc.counts[k] || ''))
    ];
    md.push('| ' + row.join(' | ') + ' |');
  }
  md.push('', 'Missing kinds per operation:');
  for (const oc of coverage.operations) {
    if (oc.missingKinds.length) md.push(`- ${oc.operationId}: ${oc.missingKinds.join(', ')}`);
  }
  md.push('', `Endpoint coverage: ${coveredOps}/${totalOps} (${endpointCoveragePct.toFixed(1)}%) have at least one scenario.`);
  // --- True Gaps Summary Matrix ---
  md.push('', 'True Gaps Summary (applicable missing kinds aggregated):');
  interface KindGapStats { applicableOps: number; missingOps: number; sampleMissing: string[]; }
  const kindStats: Record<string, KindGapStats> = {};
  for (const oc of (coverage as any).operations) {
    const appl: string[] = Array.from(applicabilityPerOp[oc.operationId].applicable);
    const missing: Set<string> = new Set(applicabilityPerOp[oc.operationId].missingApplicable);
    for (const k of appl) {
      const stat = kindStats[k] ||= { applicableOps: 0, missingOps: 0, sampleMissing: [] };
      stat.applicableOps++;
      if (missing.has(k)) {
        stat.missingOps++;
        if (stat.sampleMissing.length < 5) stat.sampleMissing.push(oc.operationId);
      }
    }
  }
  const kindRows = Object.entries(kindStats).map(([k, s]) => ({
    kind: k,
    applicableOps: s.applicableOps,
    missingOps: s.missingOps,
    missingPct: s.applicableOps ? (s.missingOps / s.applicableOps) * 100 : 0,
    sample: s.sampleMissing.join(', '),
  })).sort((a,b)=> b.missingOps - a.missingOps || a.kind.localeCompare(b.kind));
  if (kindRows.length) {
    md.push('| Kind | MissingOps | ApplicableOps | Missing% | SampleMissingOps |');
    md.push('| --- | --- | --- | --- | --- |');
    for (const r of kindRows) {
      md.push(`| ${r.kind} | ${r.missingOps} | ${r.applicableOps} | ${r.missingPct.toFixed(1)}% | ${r.sample} |`);
    }
  } else {
    md.push('- None');
  }
  // Collapsible full per-operation detail
  md.push('', '<details><summary>Full per-operation True Gaps list</summary>');
  let anyFull = false;
  for (const oc of (coverage as any).operations) {
    const missingApp: string[] = applicabilityPerOp[oc.operationId].missingApplicable;
    if (missingApp.length) { md.push(`- ${oc.operationId}: ${missingApp.join(', ')}`); anyFull = true; }
  }
  if (!anyFull) md.push('- None');
  md.push('</details>');
  md.push('', 'Applicable missing kinds are structurally possible for that operation and should be prioritized.');
  await fs.promises.writeFile(path.join(opts.outDir, 'COVERAGE.md'), md.join('\n'));
  console.log('[generate] Wrote manifest and', scenarios.length, 'scenarios');
  console.log('[generate] After dedupe:', deduped.length, 'scenarios');
  console.log('[generate] Coverage files written: COVERAGE.json, COVERAGE.md');
}

main().catch((e) => {
  console.error('[generate] FAILED', e);
  process.exit(1);
});

function fastHash(str: string): string {
  let h = 0, i = 0; const len = str.length;
  while (i < len) { h = (h * 31 + str.charCodeAt(i++)) | 0; }
  return (h >>> 0).toString(36);
}
