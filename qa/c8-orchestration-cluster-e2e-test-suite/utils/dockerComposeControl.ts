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

const execAsync = promisify(exec);

// Resolved against the CWD Playwright is invoked from (the suite's project
// root), matching how other specs in this repo reference relative paths
// (e.g. deployWithSubstitutions('./resources/*.bpmn')).
const CONFIG_DIR = path.resolve(process.cwd(), 'config');
const COMPOSE_FILES = [
  '-f',
  'docker-compose.yml',
  '-f',
  'docker-compose.analytics-isolated.yml',
];
const PROJECT_NAME = 'analytics-isolated';

// Combining docker-compose.yml with the isolated override means Compose
// validates the *entire* merged service graph up front — including the
// shared `camunda` service's `depends_on: [${DATABASE}]` — even when
// targeting only the isolated services with --no-deps. DATABASE just needs
// any valid value to satisfy that validation; the isolated services don't
// use it themselves.
const COMPOSE_ENV = {
  ...process.env,
  DATABASE: (process.env.DATABASE ?? 'elasticsearch').toLowerCase(),
};

function composeCommand(args: string[]): string {
  return ['docker compose', ...COMPOSE_FILES, '-p', PROJECT_NAME, ...args].join(
    ' ',
  );
}

/**
 * Brings up the isolated environment with the exporter NOT configured at
 * all — for the test proving it's disabled by default. Intentionally
 * isolated from the shared, long-running stack every other test depends
 * on — see config/docker-compose.analytics-isolated.yml.
 */
export async function startIsolatedEnvironmentWithoutExporter(): Promise<void> {
  await execAsync(
    composeCommand([
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
    composeCommand([
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

/** Tears down the isolated environment (whichever camunda variant is running). Safe to call even if it was never started. */
export async function stopIsolatedEnvironment(): Promise<void> {
  await execAsync(composeCommand(['down', '-v']), {
    cwd: CONFIG_DIR,
    env: COMPOSE_ENV,
  });
}

const ISOLATED_SERVICE_NAMES = [
  'camunda-analytics-isolated',
  'camunda-analytics-isolated-exporter',
  'otel-collector-isolated',
  'loki-isolated',
  'prometheus-isolated',
];

/** Fetches recent logs from a service in the isolated environment, for assertions/debugging. */
export async function getIsolatedServiceLogs(
  serviceName: string,
): Promise<string> {
  if (!ISOLATED_SERVICE_NAMES.includes(serviceName)) {
    throw new Error(
      `Unknown isolated service "${serviceName}" — expected one of ${ISOLATED_SERVICE_NAMES.join(', ')}`,
    );
  }
  const {stdout, stderr} = await execAsync(
    composeCommand(['logs', '--no-color', serviceName]),
    {cwd: CONFIG_DIR, env: COMPOSE_ENV, maxBuffer: 10 * 1024 * 1024},
  );
  return stdout + stderr;
}
