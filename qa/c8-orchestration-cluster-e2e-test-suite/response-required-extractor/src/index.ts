import { writeFileSync, mkdirSync, rmSync } from 'node:fs';
import { join } from 'node:path';
import crypto from 'node:crypto';
import yaml from 'js-yaml';
import { fileURLToPath } from 'node:url';
import { sparseCheckout } from './lib/git.js';
import { OutputFile, OutputMapEntry, OutputSchema } from './lib/types.js';
import { buildSchemaTree } from './lib/child-expansion.js';

const REPO = 'https://github.com/camunda/camunda-orchestration-cluster-api.git';
const SPEC_PATH = 'specification/rest-api.yaml';

function resolveResponse(resp: any, doc: any): any {
  if (resp && resp.$ref) {
    const refName = resp.$ref.split('/').pop();
    return doc.components?.responses?.[refName!] || resp;
  }
  return resp;
}

export function extractResponses(doc: any): OutputMapEntry[] {
  const entries: OutputMapEntry[] = [];
  const paths = doc.paths || {};
  for (const [path, pathItem] of Object.entries<any>(paths)) {
    for (const method of ['get', 'post', 'put', 'patch', 'delete']) {
      const op = pathItem[method];
      if (!op) continue;
      const responses = op.responses || {};
      for (const [status, response] of Object.entries<any>(responses)) {
        const resolved = resolveResponse(response, doc);
        const content = resolved?.content;
        const appJson = content?.['application/json'];
        const schema = appJson?.schema;
        if (!schema) continue;
        const flattened = buildSchemaTree(schema, doc.components?.schemas || {});
        entries.push({
          path,
          method: method.toUpperCase(),
          status,
          schema: { required: flattened.required, optional: flattened.optional },
        });
      }
    }
  }
  return entries;
}

export function pruneSchema(schema: OutputSchema) {
  const visit = (sch: OutputSchema) => {
    for (const group of [sch.required, sch.optional]) {
      for (const field of group) {
        if (field.children) {
          visit(field.children);
          if (field.children.required.length === 0 && field.children.optional.length === 0) {
            delete field.children;
          }
        }
      }
    }
  };
  visit(schema);
}

export async function generate() {
  const { workdir, commit, specContent } = sparseCheckout(REPO, SPEC_PATH);
  try {
    const doc: any = yaml.load(specContent);
    const responses = extractResponses(doc);
    for (const entry of responses) pruneSchema(entry.schema);
    const sha256 = crypto.createHash('sha256').update(specContent).digest('hex');
    const out: OutputFile = {
      metadata: { sourceRepo: REPO, commit, generatedAt: new Date().toISOString(), specPath: SPEC_PATH, specSha256: sha256 },
      responses,
    };
    const outDir = process.env.OUTPUT_DIR || 'output';
    mkdirSync(outDir, { recursive: true });
    writeFileSync(join(outDir, 'responses.json'), JSON.stringify(out, null, 2));
    console.log(`Extracted ${responses.length} response schemas from commit ${commit}`);
  } finally {
    if (process.env.PRESERVE_SPEC_CHECKOUT) {
      console.log('Preserving temporary spec checkout.');
    } else {
      try { rmSync(workdir, { recursive: true, force: true }); } catch {}
    }
  }
}

const isMain = process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1];
if (isMain) {
  generate();
}
