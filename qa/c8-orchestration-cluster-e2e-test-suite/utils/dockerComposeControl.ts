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

// The Operate spec and the API spec both drive this same isolated stack
// (same project name, same host ports) and can be scheduled concurrently in
// different Playwright workers/projects. mkdirSync is atomic, so this acts
// as a cross-process mutex: only one caller can be mid-lifecycle
// (start...stop) at a time; a second caller blocks in startIsolatedEnvironmentWaitStatesOff()
// until the first one's stopIsolatedEnvironment() releases the lock.
const LOCK_DIR = path.join(os.tmpdir(), 'waitstates-isolated.lock');
const LOCK_TIMEOUT_MS = 5 * 60 * 1000;
const LOCK_RETRY_MS = 1_000;

async function acquireLock(): Promise<void> {
  const start = Date.now();
  for (;;) {
    try {
      mkdirSync(LOCK_DIR);
      return;
    } catch (error) {
      if ((error as NodeJS.ErrnoException).code !== 'EEXIST') {
        throw error;
      }
      if (Date.now() - start > LOCK_TIMEOUT_MS) {
        throw new Error(
          `Timed out after ${LOCK_TIMEOUT_MS}ms waiting for the wait-states isolated-stack lock (${LOCK_DIR})`,
        );
      }
      await new Promise((resolve) => setTimeout(resolve, LOCK_RETRY_MS));
    }
  }
}

function releaseLock(): void {
  rmSync(LOCK_DIR, {recursive: true, force: true});
}

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
  await acquireLock();
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

/** Tears down the isolated wait-states environment and releases the lock. Safe to call even if it was never started. */
export async function stopIsolatedEnvironment(): Promise<void> {
  try {
    await execAsync(composeCommand(['down', '-v']), {
      cwd: CONFIG_DIR,
      env: COMPOSE_ENV,
    });
  } finally {
    releaseLock();
  }
}
