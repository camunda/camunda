import fs from 'fs';
import path from 'path';
import prettier from 'prettier';
import {ValidationScenario} from '../model/types.js';
import {LICENSE_HEADER} from './licenseHeader.js';

interface EmitOpts {
  outDir: string;
  qaImportDepth: number;
  specCommit?: string;
  generationTimestamp?: string;
}

export async function emitQaTests(
  scenarios: ValidationScenario[],
  opts: EmitOpts,
) {
  const byFile = new Map<string, ValidationScenario[]>();
  for (const s of scenarios) {
    const resource = deriveResource(s.path);
    const file = `${resource}-validation-api-tests.spec.ts`;
    const arr = byFile.get(file) || [];
    arr.push(s);
    byFile.set(file, arr);
  }
  await fs.promises.mkdir(opts.outDir, {recursive: true});
  // Resolve Prettier config once (fail fast if not found / cannot load)
  let resolvedConfig: prettier.Config | null = null;
  try {
    resolvedConfig = await prettier.resolveConfig(process.cwd());
  } catch (e) {
    throw new Error(
      '[emit] Failed to resolve Prettier config: ' +
        (e instanceof Error ? e.message : String(e)),
    );
  }
  if (!resolvedConfig) {
    // Provide a deterministic fallback matching QA expectations
    resolvedConfig = {
      singleQuote: true,
      trailingComma: 'all',
      bracketSpacing: false,
    } as prettier.Config;
    console.warn(
      '[emit] No Prettier config found. Using built-in fallback config { singleQuote:true, trailingComma:"all", bracketSpacing:false }',
    );
  }
  for (const [file, list] of byFile.entries()) {
    list.sort((a, b) => a.id.localeCompare(b.id));
    const raw = buildFile(
      list,
      opts.qaImportDepth,
      opts.specCommit,
      opts.generationTimestamp,
    );
    let formatted: string;
    try {
      formatted = await prettier.format(raw, {
        ...(resolvedConfig || {}),
        filepath: file,
      });
    } catch (e) {
      throw new Error(
        '[emit] Prettier formatting failed: ' +
          (e instanceof Error ? e.message : String(e)),
      );
    }
    const target = path.join(opts.outDir, file);
    await fs.promises.writeFile(target, formatted, 'utf8');
    console.log('[emit] wrote', target);
  }
}

function buildFile(
  scenarios: ValidationScenario[],
  depth: number,
  specCommit?: string,
  ts?: string,
): string {
  const resource = deriveResource(scenarios[0].path);
  const describeTitle = `${capitalize(resource)} Validation API Tests`;
  const up = '../'.repeat(depth);
  const httpImport = `${up}utils/http`;
  const lines: string[] = [];
  lines.push(LICENSE_HEADER.trimEnd());
  const meta: string[] = [];
  meta.push(''); // ESLint requires a new line after the license header
  meta.push('/*');
  meta.push(' * GENERATED FILE - DO NOT EDIT MANUALLY');
  meta.push(` * Generated At: ${ts || new Date().toISOString()}`);
  if (specCommit) meta.push(` * Spec Commit: ${specCommit}`);
  meta.push(' */');
  lines.push(meta.join('\n'));
  lines.push("import {test, expect} from '@playwright/test'");
  lines.push(`import {jsonHeaders, buildUrl} from '${httpImport}'`);
  lines.push('');
  lines.push(`test.describe('${describeTitle}', () => {`);
  // Pre-compute base titles and detect duplicates for uniqueness
  const baseTitles: string[] = scenarios.map((s) => buildBaseTitle(s));
  const counts = new Map<string, number>();
  for (const t of baseTitles) counts.set(t, (counts.get(t) || 0) + 1);
  const occurrence = new Map<string, number>();
  for (let i = 0; i < scenarios.length; i++) {
    const s = scenarios[i];
    const base = baseTitles[i];
    let finalTitle = base;
    if ((counts.get(base) || 0) > 1) {
      // Append a stable sequence number for readability
      const n = (occurrence.get(base) || 0) + 1;
      occurrence.set(base, n);
      finalTitle = `${base} (#${n})`;
    }
    lines.push(renderScenario(s, finalTitle));
  }
  lines.push('});');
  lines.push('');
  return lines.join('\n');
}

function renderScenario(s: ValidationScenario, title: string): string {
  const lines: string[] = [];
  lines.push(`  test(${JSON.stringify(title)}, async ({request}) => {`);
  const paramsLit = s.params ? JSON.stringify(s.params) : 'undefined';
  const urlCall = `buildUrl(${JSON.stringify(s.path.replace(/\{([^}]+)}/g, '{$1}'))}, ${paramsLit})`;
  if (s.bodyEncoding === 'multipart' && s.multipartForm) {
    const formLit = JSON.stringify(s.multipartForm, null, 2);
    lines.push(`    const formData = new FormData();`);
    lines.push(
      `    const multipartFields: Record<string,string> = ${formLit};`,
    );
    lines.push(
      `    for (const [k,v] of Object.entries(multipartFields)) formData.append(k, v);`,
    );
  } else if (s.requestBody) {
    const body = JSON.stringify(s.requestBody, null, 2);
    if (body === '[]') {
      lines.push(`    const requestBody: string[] = ${body};`);
    } else {
      lines.push(`    const requestBody = ${body};`);
    }
  }
  const headersExpr = s.headersAuth
    ? s.bodyEncoding === 'multipart'
      ? '{}'
      : 'jsonHeaders()'
    : '{}';
  const dataPart =
    s.bodyEncoding === 'multipart' && s.multipartForm
      ? ',\n      multipart: formData'
      : s.requestBody
        ? ',\n      data: requestBody'
        : '';
  lines.push('    const res = await request.' + methodFn(s.method) + '(');
  lines.push(`      ${urlCall}, {`);
  lines.push(`        headers: ${headersExpr}${dataPart}`);
  lines.push('      }');
  lines.push('    );');
  lines.push(
    ' // Conditionals are banned by eslint in qa tests. The following block can be uncommented for debugging purposes. ',
  );
  lines.push(` //   if (res.status() !== ${s.expectedStatus}) {`);
  lines.push(' //     try { console.error(await res.text()); } catch {}');
  lines.push(' //   }');
  lines.push(`    expect(res.status()).toBe(${s.expectedStatus});`);
  lines.push('  });');
  return lines.join('\n');
}

function methodFn(m: string): string {
  switch (m.toUpperCase()) {
    case 'GET':
      return 'get';
    case 'POST':
      return 'post';
    case 'PUT':
      return 'put';
    case 'DELETE':
      return 'delete';
    case 'PATCH':
      return 'patch';
    default:
      return m.toLowerCase();
  }
}

function deriveResource(p: string): string {
  const cleaned = p.startsWith('/') ? p.slice(1) : p;
  const segs = cleaned.split('/');
  if (segs[0] === 'v1' || segs[0] === 'v2')
    return (segs[1] || 'root').replace(/[^a-zA-Z0-9]/g, '');
  return (segs[0] || 'root').replace(/[^a-zA-Z0-9]/g, '');
}

function buildBaseTitle(s: ValidationScenario): string {
  // Provide human friendly titles per scenario kind; keep concise so appended (#n) still fits nicely.
  // Specialization: param constraint violations (path/query) should surface the specific constraint kind.
  if (s.type === 'param-constraint-violation') {
    const constraint = s.constraintKind || 'constraint';
    const targetSegs = s.target ? s.target.split('.') : [];
    const location = targetSegs.length > 1 ? targetSegs[0] : 'param';
    const paramName = targetSegs.length
      ? targetSegs[targetSegs.length - 1]
      : 'param';
    const locLabel =
      location === 'path'
        ? 'Path param'
        : location === 'query'
          ? 'Query param'
          : 'Param';
    return `${s.operationId} - ${locLabel} ${paramName} ${constraint} violation`;
  }
  switch (s.type) {
    case 'missing-required':
      return `${s.operationId} - Missing ${s.target}`;
    case 'missing-required-combo':
      return `${s.operationId} - Missing combo ${s.target}`;
    case 'param-missing':
      return `${s.operationId} - Missing param ${s.target}`;
    case 'type-mismatch':
      return `${s.operationId} - Param ${s.target} wrong type`;
    case 'param-type-mismatch':
      return `${s.operationId} - Param ${s.target} wrong type`;
    case 'body-top-type-mismatch':
      return `${s.operationId} - Body wrong top-level type`;
    case 'missing-body':
      return `${s.operationId} - Missing body`;
    case 'union':
      return `${s.operationId} - oneOf violation`;
    case 'oneof-ambiguous':
      return `${s.operationId} - oneOf ambiguous`;
    case 'oneof-multi-ambiguous':
      return `${s.operationId} - oneOf multi ambiguous`;
    case 'oneof-cross-bleed':
      return `${s.operationId} - oneOf cross bleed`;
    case 'oneof-none-match':
      return `${s.operationId} - oneOf none match`;
    case 'constraint-violation':
      return `${s.operationId} - Constraint violation ${s.target}`;
    case 'enum-violation':
      return `${s.operationId} - Enum violation ${s.target}`;
    case 'additional-prop':
      return `${s.operationId} - Additional prop ${s.target}`;
    case 'additional-prop-general':
      return `${s.operationId} - Additional prop ${s.target}`;
    case 'nested-additional-prop':
      return `${s.operationId} - Nested additional prop ${s.target}`;
    case 'unique-items-violation':
      return `${s.operationId} - uniqueItems violation ${s.target}`;
    case 'multiple-of-violation':
      return `${s.operationId} - multipleOf violation ${s.target}`;
    case 'format-invalid':
      return `${s.operationId} - format invalid ${s.target}`;
    case 'discriminator-mismatch':
      return `${s.operationId} - discriminator mismatch`;
    case 'discriminator-structure-mismatch':
      return `${s.operationId} - discriminator structure mismatch`;
    case 'allof-missing-required':
      return `${s.operationId} - allOf missing required`;
    case 'allof-conflict':
      return `${s.operationId} - allOf conflict`;
    default:
      return s.id; // Fallback is globally unique id
  }
}

function capitalize(s: string): string {
  return s.charAt(0).toUpperCase() + s.slice(1);
}
