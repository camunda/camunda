#!/usr/bin/env tsx
import fs from 'fs';
import path from 'path';
import https from 'https';

const RAW_URL =
  'https://raw.githubusercontent.com/camunda/camunda-orchestration-cluster-api/main/specification/rest-api.yaml';
const COMMITS_API =
  'https://api.github.com/repos/camunda/camunda-orchestration-cluster-api/commits?path=specification/rest-api.yaml&per_page=1';
const cacheDir = path.resolve(process.cwd(), 'cache');
const target = path.join(cacheDir, 'rest-api.yaml');
const commitFile = path.join(cacheDir, 'spec-commit.txt');

async function main() {
  await fs.promises.mkdir(cacheDir, {recursive: true});
  console.log('[fetch-spec] Downloading spec...');
  const data = await download(RAW_URL);
  await fs.promises.writeFile(target, data, 'utf8');
  console.log('[fetch-spec] Wrote', target, `(bytes=${data.length})`);
  // Attempt to resolve the latest commit hash for the spec path
  try {
    const metaJson = await download(COMMITS_API, {
      headers: {'User-Agent': 'request-validation-generator'},
    });
    const parsed = JSON.parse(metaJson);
    const sha: string | undefined = Array.isArray(parsed) && parsed[0]?.sha;
    if (sha) {
      await fs.promises.writeFile(commitFile, sha.trim() + '\n', 'utf8');
      console.log('[fetch-spec] Commit hash:', sha);
    } else {
      console.warn(
        '[fetch-spec] Could not extract commit SHA from API response',
      );
    }
  } catch (e) {
    console.warn(
      '[fetch-spec] Unable to fetch commit hash (non-fatal):',
      (e as Error).message,
    );
  }
}

function download(
  url: string,
  options?: {headers?: Record<string, string>},
): Promise<string> {
  return new Promise((resolve, reject) => {
    const req = https.request(
      url,
      {method: 'GET', headers: options?.headers},
      (res) => {
        if (res.statusCode && res.statusCode >= 400) {
          reject(new Error('HTTP ' + res.statusCode));
          return;
        }
        const chunks: Buffer[] = [];
        res.on('data', (c) => chunks.push(c));
        res.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')));
      },
    );
    req.on('error', reject);
    req.end();
  });
}

main().catch((e) => {
  console.error('[fetch-spec] FAILED', e);
  process.exitCode = 1;
});
