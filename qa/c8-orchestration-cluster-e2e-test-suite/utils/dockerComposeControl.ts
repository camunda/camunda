/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {exec} from 'node:child_process';
import {promisify} from 'node:util';
import path from 'node:path';
import {mkdirSync, rmSync} from 'node:fs';
import os from 'node:os';

const execAsync = promisify(exec);

// Resolved against the CWD Playwright is invoked from (the suite's project
// root), matching how other specs in this repo reference relative paths
// (e.g. deployWithSubstitutions('./resources/*.bpmn')).
const CONFIG_DIR = path.resolve(process.cwd(), 'config');

// Merging docker-compose.yml pulls in the shared `camunda` service's
// `depends_on: [${DATABASE}]`, which Compose validates even though we only
// target the isolated services. DATABASE must be a literal service name
// (postgres/elasticsearch/opensearch) — forwarding process.env.DATABASE
// verbatim breaks when it's e.g. "RDBMS", so hardcode a valid one.
const COMPOSE_ENV = {
  ...process.env,
  DATABASE: 'elasticsearch',
};

function composeCommand(
  composeFiles: string[],
  projectName: string,
  args: string[],
): string {
  return ['docker compose', ...composeFiles, '-p', projectName, ...args].join(
    ' ',
  );
}

// ---------------------------------------------------------------------------
// Analytics-exporter isolated environment
// ---------------------------------------------------------------------------

const ANALYTICS_COMPOSE_FILES = [
  '-f',
  'docker-compose.yml',
  '-f',
  'docker-compose.analytics-isolated.yml',
];
const ANALYTICS_PROJECT_NAME = 'analytics-isolated';

/**
 * Brings up the isolated environment with the exporter NOT configured at
 * all — for the test proving it's disabled by default. Intentionally
 * isolated from the shared, long-running stack every other test depends
 * on — see config/docker-compose.analytics-isolated.yml.
 */
export async function startIsolatedEnvironmentWithoutExporter(): Promise<void> {
  await execAsync(
    composeCommand(ANALYTICS_COMPOSE_FILES, ANALYTICS_PROJECT_NAME, [
      'up',
      '-d',
      '--no-deps',
      'camunda-analytics-isolated',
      'otel-collector-isolated',
      'loki-isolated',
      'prometheus-isolated',
    ]),
    {cwd: CONFIG_DIR, env: COMPOSE_ENV},
  );
}

/**
 * Brings up the isolated environment with the exporter enabled — for the
 * counter vs. raw-event-count parity test. A separate camunda variant from
 * the one above, on the same host ports; only one is ever started at a
 * time. See config/docker-compose.analytics-isolated.yml.
 */
export async function startIsolatedEnvironmentWithExporter(): Promise<void> {
  await execAsync(
    composeCommand(ANALYTICS_COMPOSE_FILES, ANALYTICS_PROJECT_NAME, [
      'up',
      '-d',
      '--no-deps',
      'camunda-analytics-isolated-exporter',
      'otel-collector-isolated',
      'loki-isolated',
      'prometheus-isolated',
    ]),
    {cwd: CONFIG_DIR, env: COMPOSE_ENV},
  );
}

/** Tears down the analytics-exporter isolated environment (whichever camunda variant is running). Safe to call even if it was never started. */
export async function stopIsolatedEnvironment(): Promise<void> {
  await execAsync(
    composeCommand(ANALYTICS_COMPOSE_FILES, ANALYTICS_PROJECT_NAME, [
      'down',
      '-v',
    ]),
    {cwd: CONFIG_DIR, env: COMPOSE_ENV},
  );
}

const ISOLATED_SERVICE_NAMES = [
  'camunda-analytics-isolated',
  'camunda-analytics-isolated-exporter',
  'otel-collector-isolated',
  'loki-isolated',
  'prometheus-isolated',
];

/** Fetches recent logs from a service in the analytics-exporter isolated environment, for assertions/debugging. */
export async function getIsolatedServiceLogs(
  serviceName: string,
): Promise<string> {
  if (!ISOLATED_SERVICE_NAMES.includes(serviceName)) {
    throw new Error(
      `Unknown isolated service "${serviceName}" — expected one of ${ISOLATED_SERVICE_NAMES.join(', ')}`,
    );
  }
  const {stdout, stderr} = await execAsync(
    composeCommand(ANALYTICS_COMPOSE_FILES, ANALYTICS_PROJECT_NAME, [
      'logs',
      '--no-color',
      serviceName,
    ]),
    {cwd: CONFIG_DIR, env: COMPOSE_ENV, maxBuffer: 10 * 1024 * 1024},
  );
  return stdout + stderr;
}

// ---------------------------------------------------------------------------
// Operate wait-states isolated environment
// ---------------------------------------------------------------------------

const WAITSTATES_COMPOSE_FILES = [
  '-f',
  'docker-compose.yml',
  '-f',
  'docker-compose.waitstates-isolated.yml',
];
const WAITSTATES_PROJECT_NAME = 'waitstates-isolated';

// The Operate spec and the API spec both drive this same isolated stack
// (same project name, same host ports) and can be scheduled concurrently in
// different Playwright workers/projects. mkdirSync is atomic, so this acts
// as a cross-process mutex: only one caller can be mid-lifecycle
// (start...stop) at a time; a second caller blocks in
// startIsolatedEnvironmentWaitStatesOff() until the first one's
// stopIsolatedEnvironmentWaitStates() releases the lock.
const WAITSTATES_LOCK_DIR = path.join(os.tmpdir(), 'waitstates-isolated.lock');
const WAITSTATES_LOCK_TIMEOUT_MS = 5 * 60 * 1000;
const WAITSTATES_LOCK_RETRY_MS = 1_000;

async function acquireWaitStatesLock(): Promise<void> {
  const start = Date.now();
  for (;;) {
    try {
      mkdirSync(WAITSTATES_LOCK_DIR);
      return;
    } catch (error) {
      if ((error as NodeJS.ErrnoException).code !== 'EEXIST') {
        throw error;
      }
      if (Date.now() - start > WAITSTATES_LOCK_TIMEOUT_MS) {
        throw new Error(
          `Timed out after ${WAITSTATES_LOCK_TIMEOUT_MS}ms waiting for the wait-states isolated-stack lock (${WAITSTATES_LOCK_DIR})`,
        );
      }
      await new Promise((resolve) =>
        setTimeout(resolve, WAITSTATES_LOCK_RETRY_MS),
      );
    }
  }
}

function releaseWaitStatesLock(): void {
  rmSync(WAITSTATES_LOCK_DIR, {recursive: true, force: true});
}

export async function startIsolatedEnvironmentWaitStatesOff(): Promise<void> {
  await acquireWaitStatesLock();
  await execAsync(
    composeCommand(WAITSTATES_COMPOSE_FILES, WAITSTATES_PROJECT_NAME, [
      'up',
      '-d',
      '--no-deps',
      'camunda-waitstates-isolated-off',
      'elasticsearch-waitstates-isolated',
    ]),
    {cwd: CONFIG_DIR, env: COMPOSE_ENV},
  );
}

/** Tears down the isolated wait-states environment and releases the lock. Safe to call even if it was never started. */
export async function stopIsolatedEnvironmentWaitStates(): Promise<void> {
  try {
    await execAsync(
      composeCommand(WAITSTATES_COMPOSE_FILES, WAITSTATES_PROJECT_NAME, [
        'down',
        '-v',
      ]),
      {cwd: CONFIG_DIR, env: COMPOSE_ENV},
    );
  } finally {
    releaseWaitStatesLock();
  }
}
