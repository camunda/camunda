/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {readdir, readFile, writeFile, mkdir, rm} from 'node:fs/promises';
import {join, dirname} from 'node:path';
import {fileURLToPath} from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const ROOT = join(__dirname, '..');
const COMPONENTS_DIR = join(ROOT, 'src/components/ui');
const OUTPUT_DIR = join(ROOT, 'registry');

const PACKAGE_NAME = '@camunda/design-system';
const HOMEPAGE = 'https://design.camunda.io';

const onlyArgs = process.argv.slice(2);

function pascalCase(kebab) {
  return kebab
    .split('-')
    .map((s) => s[0].toUpperCase() + s.slice(1))
    .join('');
}

async function readOptional(path) {
  try {
    return await readFile(path, 'utf8');
  } catch {
    return null;
  }
}

function stripMdxPreamble(mdxSource) {
  return mdxSource
    .replace(/\{\/\*[\s\S]*?\*\/\}/g, '')
    .replace(/^[ \t]*(?:import|export)\s+[\s\S]*?(?:;|\n\n)/gm, '')
    .replace(/<Meta\b[\s\S]*?\/>/g, '')
    .trim();
}

async function discoverComponents() {
  const entries = await readdir(COMPONENTS_DIR, {withFileTypes: true});
  const all = entries.filter((e) => e.isDirectory()).map((e) => e.name).sort();
  if (onlyArgs.length === 0) return all;
  const set = new Set(onlyArgs);
  const filtered = all.filter((c) => set.has(c));
  const missing = onlyArgs.filter((c) => !all.includes(c));
  if (missing.length > 0) {
    throw new Error(`Unknown component(s): ${missing.join(', ')}`);
  }
  return filtered;
}

function extractTitle(component, mdSource) {
  if (mdSource) {
    const match = mdSource.match(/^#\s+(.+?)(?:\s+—\s+.*)?$/m);
    if (match) return match[1].trim();
  }
  return pascalCase(component);
}

function extractDescription(component, mdSource) {
  if (mdSource) {
    const lines = mdSource.split('\n');
    const start = lines.findIndex(
      (l) => !l.startsWith('#') && l.trim().length > 0,
    );
    if (start !== -1) {
      const para = [];
      for (let i = start; i < lines.length; i++) {
        const line = lines[i];
        if (line.trim().length === 0 || line.startsWith('#')) break;
        para.push(line.trim());
      }
      if (para.length > 0) return para.join(' ');
    }
  }
  return `${pascalCase(component)} component.`;
}

function extractDependencies(source) {
  const deps = new Set();
  const re = /from\s+['"]([^'"]+)['"]/g;
  let match;
  while ((match = re.exec(source)) !== null) {
    const spec = match[1];
    if (spec.startsWith('.') || spec.startsWith('@/')) continue;
    if (
      spec === 'react' ||
      spec === 'react-dom' ||
      spec.startsWith('react/') ||
      spec.startsWith('react-dom/')
    ) {
      continue;
    }
    const parts = spec.split('/');
    const pkg = spec.startsWith('@')
      ? parts.slice(0, 2).join('/')
      : parts[0];
    deps.add(pkg);
  }
  return [...deps].sort();
}

async function buildItem(component) {
  const dir = join(COMPONENTS_DIR, component);
  const sourcePath = join(dir, `${component}.shadcn.tsx`);
  const source = await readOptional(sourcePath);
  if (source === null) return null;
  const docsMdx = await readOptional(join(dir, `${component}.docs.mdx`));
  if (docsMdx === null) return null;
  const docs = stripMdxPreamble(docsMdx);
  const migrationMdx = await readOptional(
    join(dir, `${component}.migration.mdx`),
  );
  const migrationGuide = migrationMdx ? stripMdxPreamble(migrationMdx) : null;

  const exportName = pascalCase(component);

  return {
    $schema: 'https://ui.shadcn.com/schema/registry-item.json',
    name: component,
    type: 'registry:ui',
    title: extractTitle(component, docs),
    description: extractDescription(component, docs),
    dependencies: extractDependencies(source),
    files: [
      {
        path: `components/ui/${component}.tsx`,
        type: 'registry:ui',
        content: source,
      },
    ],
    meta: {
      package: PACKAGE_NAME,
      import: `import {${exportName}} from '${PACKAGE_NAME}';`,
      ...(docs ? {docs} : {}),
      ...(migrationGuide ? {migrationGuide} : {}),
    },
  };
}

async function buildRegistry(components) {
  const items = (
    await Promise.all(components.map(buildItem))
  ).filter(Boolean);
  await mkdir(join(OUTPUT_DIR, 'r'), {recursive: true});
  await Promise.all(
    items.map((item) =>
      writeFile(
        join(OUTPUT_DIR, 'r', `${item.name}.json`),
        JSON.stringify(item, null, 2) + '\n',
      ),
    ),
  );
  const index = {
    $schema: 'https://ui.shadcn.com/schema/registry.json',
    name: '@camunda',
    homepage: HOMEPAGE,
    items: items.map((item) => ({
      name: item.name,
      type: item.type,
      title: item.title,
      description: item.description,
    })),
  };
  await writeFile(
    join(OUTPUT_DIR, 'r', 'registry.json'),
    JSON.stringify(index, null, 2) + '\n',
  );
  return items.length;
}

async function main() {
  const components = await discoverComponents();
  if (components.length === 0) {
    console.error('No components matched.');
    process.exit(1);
  }
  await rm(OUTPUT_DIR, {recursive: true, force: true});
  const count = await buildRegistry(components);
  console.log(`Built registry: ${count} item${count === 1 ? '' : 's'}.`);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
