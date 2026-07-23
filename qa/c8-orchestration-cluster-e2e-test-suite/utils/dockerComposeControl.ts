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

const CONFIG_DIR = path.resolve(process.cwd(), 'config');
const COMPOSE_FILES = [
  '-f',
  'docker-compose.yml',
  '-f',
  'docker-compose.waitstates-isolated.yml',
];
const PROJECT_NAME = 'waitstates-isolated';

// Merging docker-compose.yml pulls in the shared `camunda` service's
// `depends_on: [${DATABASE}]`, which Compose validates even though we only
// target the isolated services. DATABASE must be a literal service name
// (postgres/elasticsearch/opensearch) — forwarding process.env.DATABASE
// verbatim breaks when it's e.g. "RDBMS", so hardcode a valid one.
const COMPOSE_ENV = {
  ...process.env,
  DATABASE: 'elasticsearch',
};

function composeCommand(args: string[]): string {
  return ['docker compose', ...COMPOSE_FILES, '-p', PROJECT_NAME, ...args].join(
    ' ',
  );
}

export async function startIsolatedEnvironmentWaitStatesOff(): Promise<void> {
  await execAsync(
    composeCommand([
      'up',
      '-d',
      '--no-deps',
      'camunda-waitstates-isolated-off',
      'elasticsearch-waitstates-isolated',
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
