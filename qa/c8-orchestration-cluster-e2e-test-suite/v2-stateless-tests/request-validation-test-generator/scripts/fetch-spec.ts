#!/usr/bin/env tsx
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import fs from 'fs';
import path from 'path';
import {execFileSync} from 'child_process';
import {fileURLToPath} from 'url';

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const SOURCE_SPEC_DIR = path.resolve(
  scriptDir,
  '../../../../../zeebe/gateway-protocol/src/main/proto/v2',
);
const SOURCE_SPEC_PATH = path.join(SOURCE_SPEC_DIR, 'rest-api.yaml');
const cacheDir = path.resolve(process.cwd(), 'cache');
const commitFile = path.join(cacheDir, 'spec-commit.txt');

async function main() {
  await fs.promises.mkdir(cacheDir, {recursive: true});
  if (!fs.existsSync(SOURCE_SPEC_PATH)) {
    throw new Error(
      `[fetch-spec] Spec not found at ${SOURCE_SPEC_PATH}. Expected the ` +
        'in-repo OpenAPI root (zeebe/gateway-protocol/src/main/proto/v2/rest-api.yaml).',
    );
  }
  console.log('[fetch-spec] Using in-repo spec at', SOURCE_SPEC_PATH);
  try {
    const sha = execFileSync(
      'git',
      ['log', '-1', '--format=%H', '--', SOURCE_SPEC_DIR],
      {cwd: SOURCE_SPEC_DIR, encoding: 'utf8'},
    ).trim();
    if (sha) {
      await fs.promises.writeFile(commitFile, sha + '\n', 'utf8');
      console.log('[fetch-spec] Spec commit:', sha);
    }
  } catch (e) {
    console.warn(
      '[fetch-spec] Unable to resolve spec commit hash (non-fatal):',
      (e as Error).message,
    );
  }
}

main().catch((e) => {
  console.error('[fetch-spec] FAILED', e);
  process.exitCode = 1;
});
