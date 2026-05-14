#!/usr/bin/env node
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/**
 * Test harness for the real Bedrock-backed LLM. Calls the same Anthropic
 * tool-use endpoint via SigV4 that the browser uses for free-text prompts,
 * captures the responses, and writes them to disk for analysis.
 *
 * USAGE:
 *   node scripts/test-bedrock.mjs                  # run all prompts in DEFAULT_PROMPTS
 *   node scripts/test-bedrock.mjs --limit 5        # cap at N prompts (still respects --budget)
 *   node scripts/test-bedrock.mjs --budget 50      # hard cap on total Bedrock calls (default 50)
 *   node scripts/test-bedrock.mjs --prompt "..."   # one-off ad-hoc prompt
 *
 * Reads credentials from operate/client/.env.local. Saves responses to
 * /tmp/bedrock-test-responses.json (cumulative — appends; resets on --fresh).
 */

import {readFileSync, writeFileSync, existsSync} from 'node:fs';
import {createHash, createHmac} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {dirname, resolve} from 'node:path';

// ---------------------------------------------------------------------------
// Setup
// ---------------------------------------------------------------------------

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const envPath = resolve(__dirname, '..', '.env.local');
const RESPONSE_FILE = '/tmp/bedrock-test-responses.json';

function loadEnv() {
  if (!existsSync(envPath)) {
    console.error(`No .env.local at ${envPath}`);
    process.exit(1);
  }
  const env = {};
  for (const line of readFileSync(envPath, 'utf8').split('\n')) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    const eq = trimmed.indexOf('=');
    if (eq < 0) continue;
    env[trimmed.slice(0, eq).trim()] = trimmed.slice(eq + 1).trim();
  }
  return env;
}

const env = loadEnv();
const ARN = env.VITE_AWS_BEDROCK_ARN;
const ACCESS_KEY = env.VITE_AWS_ACCESS_KEY_ID;
const SECRET_KEY = env.VITE_AWS_SECRET_ACCESS_KEY;

if (!ARN || !ACCESS_KEY || !SECRET_KEY) {
  console.error('Missing one of VITE_AWS_BEDROCK_ARN, VITE_AWS_ACCESS_KEY_ID, VITE_AWS_SECRET_ACCESS_KEY in .env.local');
  process.exit(1);
}

// ---------------------------------------------------------------------------
// Argument parsing
// ---------------------------------------------------------------------------

const args = process.argv.slice(2);
function arg(name, def) {
  const i = args.indexOf(name);
  return i >= 0 ? args[i + 1] : def;
}
const flag = (name) => args.includes(name);

const BUDGET = parseInt(arg('--budget', '50'), 10);
const LIMIT = parseInt(arg('--limit', '999'), 10);
const ONE_PROMPT = arg('--prompt', null);
const FRESH = flag('--fresh');

// ---------------------------------------------------------------------------
// SigV4 (mirrors browser awsSigV4.ts)
// ---------------------------------------------------------------------------

function sha256Hex(s) {
  return createHash('sha256').update(s).digest('hex');
}

function hmac(key, data) {
  return createHmac('sha256', key).update(data).digest();
}

function deriveSigningKey(secretKey, dateStamp, region, service) {
  const kDate = hmac('AWS4' + secretKey, dateStamp);
  const kRegion = hmac(kDate, region);
  const kService = hmac(kRegion, service);
  return hmac(kService, 'aws4_request');
}

function extractRegion(arn) {
  const parts = arn.split(':');
  if (parts.length < 6 || parts[0] !== 'arn' || parts[2] !== 'bedrock' || !parts[3]) {
    throw new Error('Invalid ARN: ' + arn);
  }
  return parts[3];
}

function signRequest(host, path, body) {
  const region = extractRegion(ARN);
  const now = new Date();
  const amzDate = now.toISOString().replace(/[:-]/g, '').slice(0, 15) + 'Z';
  const dateStamp = amzDate.slice(0, 8);
  const payloadHash = sha256Hex(body);

  const canonicalHeaders =
    `content-type:application/json\n` +
    `host:${host}\n` +
    `x-amz-content-sha256:${payloadHash}\n` +
    `x-amz-date:${amzDate}\n`;
  const signedHeaders = 'content-type;host;x-amz-content-sha256;x-amz-date';

  // Double-encode each path segment for the canonical request (non-S3 SigV4 quirk)
  const canonicalPath = path.split('/').map(encodeURIComponent).join('/');

  const canonicalRequest = ['POST', canonicalPath, '', canonicalHeaders, signedHeaders, payloadHash].join('\n');
  const credentialScope = `${dateStamp}/${region}/bedrock/aws4_request`;
  const stringToSign = ['AWS4-HMAC-SHA256', amzDate, credentialScope, sha256Hex(canonicalRequest)].join('\n');
  const signingKey = deriveSigningKey(SECRET_KEY, dateStamp, region, 'bedrock');
  const signature = createHmac('sha256', signingKey).update(stringToSign).digest('hex');

  return {
    'content-type': 'application/json',
    host,
    'x-amz-content-sha256': payloadHash,
    'x-amz-date': amzDate,
    Authorization:
      `AWS4-HMAC-SHA256 Credential=${ACCESS_KEY}/${credentialScope},` +
      `SignedHeaders=${signedHeaders},Signature=${signature}`,
  };
}

// ---------------------------------------------------------------------------
// System prompt + tool schema (must mirror what the browser sends)
// Source: operate/client/src/App/Notebooks/llm.ts at the time of writing this script.
// If the browser side drifts, this file should be re-synced.
// ---------------------------------------------------------------------------

// Re-read the live prompt from the source so this script stays in sync with
// the browser. We extract the SYSTEM_PROMPT template literal from llm.ts.
function loadSystemPromptFromSource() {
  const src = readFileSync(
    resolve(__dirname, '..', 'src', 'App', 'Notebooks', 'llm.ts'),
    'utf8',
  );
  const match = src.match(/const SYSTEM_PROMPT\s*=\s*\n?\s*`([\s\S]*?)`\.trim\(\);/);
  if (!match) {
    throw new Error('Could not find SYSTEM_PROMPT in llm.ts');
  }
  return match[1].trim();
}
const SYSTEM_PROMPT = loadSystemPromptFromSource();

const CREATE_WIDGETS_TOOL = {
  name: 'create_widgets',
  description: 'Create an array of dashboard widgets based on the user prompt.',
  input_schema: {
    type: 'object',
    properties: {
      widgets: {
        type: 'array',
        items: {
          type: 'object',
          properties: {
            id: {type: 'string'},
            type: {
              type: 'string',
              enum: [
                'metric', 'table', 'kpi', 'chart', 'bpmn',
                'text', 'status-grid', 'activity-feed', 'funnel', 'trend', 'chart-list',
              ],
            },
            title: {type: 'string'},
            description: {type: 'string'},
            query: {
              type: 'object',
              properties: {
                endpoint: {type: 'string'},
                method: {type: 'string', enum: ['GET', 'POST']},
                body: {type: 'object'},
              },
              required: ['endpoint', 'method'],
            },
            field: {type: 'string'},
            columns: {type: 'array', items: {type: 'string'}},
            accent: {type: 'string', enum: ['info', 'success', 'warning', 'error', 'neutral']},
            chartType: {
              type: 'string',
              enum: ['bar', 'line', 'donut', 'pie', 'stacked-bar', 'stacked-area', 'meter', 'treemap', 'radar'],
            },
            chartGroupBy: {type: 'string'},
            chartValueField: {type: 'string'},
            chartStackBy: {type: 'string'},
            processDefinitionKey: {type: 'string'},
            overlay: {
              type: 'string',
              enum: ['active', 'incidents', 'combined', 'stuck', 'none', 'heatmap'],
            },
            text: {type: 'string'},
            kpis: {type: 'array', items: {type: 'object'}},
          },
          required: ['id', 'type', 'title', 'description', 'query'],
        },
      },
    },
    required: ['widgets'],
  },
};

// ---------------------------------------------------------------------------
// Bedrock call
// ---------------------------------------------------------------------------

async function callBedrock(prompt) {
  const region = extractRegion(ARN);
  const host = `bedrock-runtime.${region}.amazonaws.com`;
  const path = `/model/${encodeURIComponent(ARN)}/invoke`;
  const url = `https://${host}${path}`;

  const body = JSON.stringify({
    anthropic_version: 'bedrock-2023-05-31',
    max_tokens: 4096,
    system: SYSTEM_PROMPT,
    tools: [CREATE_WIDGETS_TOOL],
    tool_choice: {type: 'tool', name: 'create_widgets'},
    messages: [{role: 'user', content: prompt}],
  });

  const headers = signRequest(host, path, body);
  // Hard 60s timeout — Bedrock occasionally hangs without responding when its
  // edge IPs rotate out of the firewall allowlist. Better to fail fast than
  // tie up the harness.
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), 60_000);
  let res;
  try {
    res = await fetch(url, {method: 'POST', headers, body, signal: ctrl.signal});
  } finally {
    clearTimeout(timer);
  }
  const text = await res.text();
  let parsed = null;
  try {
    parsed = JSON.parse(text);
  } catch {
    /* leave as text */
  }
  return {ok: res.ok, status: res.status, body: parsed ?? text};
}

// ---------------------------------------------------------------------------
// Curated test prompts — broad but each tests something specific
// ---------------------------------------------------------------------------

const DEFAULT_PROMPTS = [
  // Cinematic multi-widget dashboards
  'Set me up with a Monday morning view',
  'Show me everything',
  // Simple single-widget asks
  'How many active process instances do I have right now?',
  'List all open incidents',
  'Show me a breakdown of incidents by error type',
  // Process-specific
  'Show me the order-process diagram with live state',
  'Where are instances getting stuck?',
  // Trends
  'Show me incident trends over the last 7 days',
  'How is workload looking over the last 24 hours?',
  // Edge cases
  'Show me jobs that are about to time out',
  'Compare order-process vs payment-process',
  'I am new to this system, give me an overview',
];

// ---------------------------------------------------------------------------
// Persistence + budget
// ---------------------------------------------------------------------------

let store = {totalCalls: 0, runs: []};
if (existsSync(RESPONSE_FILE) && !FRESH) {
  try {
    store = JSON.parse(readFileSync(RESPONSE_FILE, 'utf8'));
  } catch {
    /* start fresh on parse error */
  }
}

function persist() {
  writeFileSync(RESPONSE_FILE, JSON.stringify(store, null, 2));
}

// ---------------------------------------------------------------------------
// Main
// ---------------------------------------------------------------------------

async function main() {
  const promptsToRun = ONE_PROMPT ? [ONE_PROMPT] : DEFAULT_PROMPTS.slice(0, LIMIT);
  console.log(`[bedrock-test] starting`);
  console.log(`[bedrock-test] cumulative calls so far: ${store.totalCalls} / budget ${BUDGET}`);
  console.log(`[bedrock-test] running ${promptsToRun.length} prompt(s)`);
  console.log('');

  for (const prompt of promptsToRun) {
    if (store.totalCalls >= BUDGET) {
      console.error(`[bedrock-test] budget exhausted (${store.totalCalls}/${BUDGET}). stopping.`);
      break;
    }
    console.log(`[${store.totalCalls + 1}/${BUDGET}] PROMPT: "${prompt}"`);
    const start = Date.now();
    let result;
    try {
      result = await callBedrock(prompt);
    } catch (err) {
      result = {ok: false, status: 0, body: String(err)};
    }
    const ms = Date.now() - start;
    store.totalCalls += 1;
    store.runs.push({timestamp: new Date().toISOString(), prompt, ms, ...result});
    persist();

    if (result.ok && result.body?.content) {
      const toolBlock = result.body.content.find((b) => b.type === 'tool_use');
      if (toolBlock?.input?.widgets) {
        const widgets = toolBlock.input.widgets;
        console.log(`  ✓ ${ms}ms · ${widgets.length} widget(s):`);
        for (const w of widgets) {
          const detail = [w.type, w.title].filter(Boolean).join(' · ');
          console.log(`    - ${detail}${w.query?.endpoint ? ' [' + w.query.endpoint + ']' : ''}`);
        }
      } else {
        console.log(`  ✗ ${ms}ms · no tool_use block`);
      }
    } else {
      console.log(`  ✗ ${ms}ms · status=${result.status}`);
      const snippet = typeof result.body === 'string' ? result.body : JSON.stringify(result.body);
      console.log(`    ${snippet.slice(0, 200)}`);
    }
    console.log('');
  }

  console.log(`[bedrock-test] done. cumulative calls: ${store.totalCalls}/${BUDGET}`);
  console.log(`[bedrock-test] full responses saved to ${RESPONSE_FILE}`);
}

main().catch((err) => {
  console.error('[bedrock-test] fatal:', err);
  process.exit(1);
});
