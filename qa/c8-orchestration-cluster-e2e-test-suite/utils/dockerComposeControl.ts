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
  'docker-compose.waitstates-isolated.yml',
];
const PROJECT_NAME = 'waitstates-isolated';

// Combining docker-compose.yml with the isolated override means Compose
// validates the *entire* merged service graph up front — including the
// shared `camunda` service's `depends_on: [${DATABASE}]` — even when
// targeting only the isolated service with --no-deps. DATABASE just needs
// any valid value to satisfy that validation; the isolated service doesn't
// use it itself (it runs its own in-memory H2, see
// config/docker-compose.waitstates-isolated.yml).
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
 * Brings up the isolated environment with wait-state tracking disabled
 * (`camunda.data.wait-states.enabled=false`) — for the tests proving the
 * feature is fully off (no indexing, no UI surface) when the flag is off.
 * Every other wait-state test runs flag-on against the shared stack, so
 * there is no matching "with wait states" isolated variant here.
 */
export async function startIsolatedEnvironmentWaitStatesOff(): Promise<void> {
  await execAsync(
    composeCommand([
      'up',
      '-d',
      '--no-deps',
      'camunda-waitstates-isolated-off',
    ]),
    {cwd: CONFIG_DIR, env: COMPOSE_ENV},
  );
}

/** Tears down the isolated wait-states environment. Safe to call even if it was never started. */
export async function stopIsolatedEnvironment(): Promise<void> {
  await execAsync(composeCommand(['down', '-v']), {
    cwd: CONFIG_DIR,
    env: COMPOSE_ENV,
  });
}
