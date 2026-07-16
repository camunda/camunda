/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, request} from '@playwright/test';

// Operate's migration wizard fetches the list of available target processes
// exactly once, on mount (MigrationView's useEffect has an empty dependency
// array, and processesStore has no polling interval) — closing/reopening the
// target combobox re-renders the same already-fetched data, it never
// refetches. If a newer process version isn't indexed yet at the moment the
// wizard mounts, no amount of retrying the combobox interaction can make it
// appear; only unmounting and remounting the wizard (or a full page reload)
// triggers a fresh fetch. Poll the same REST endpoint the wizard itself uses
// (/api/processes/grouped) before entering migration mode, so the one-shot
// fetch is guaranteed to already see the target version.
export async function waitForProcessVersion(
  bpmnProcessId: string,
  minVersion: number,
  timeout = 120000,
): Promise<void> {
  const baseURL =
    process.env.CORE_APPLICATION_OPERATE_URL ?? 'http://localhost:8081';
  const username = process.env.TEST_USERNAME || 'demo';
  const password = process.env.TEST_PASSWORD || 'demo';

  const apiContext = await request.newContext({baseURL});
  await apiContext.post('/api/login', {
    headers: {'Content-Type': 'application/x-www-form-urlencoded'},
    form: {username, password},
  });

  try {
    await expect
      .poll(
        async () => {
          const response = await apiContext.post('/api/processes/grouped', {
            data: {},
          });
          const groups: {
            bpmnProcessId: string;
            processes: {version: number}[];
          }[] = await response.json();
          const group = groups.find((g) => g.bpmnProcessId === bpmnProcessId);
          if (!group) {
            return 0;
          }
          return Math.max(...group.processes.map(({version}) => version));
        },
        {timeout},
      )
      .toBeGreaterThanOrEqual(minVersion);
  } finally {
    await apiContext.dispose();
  }
}
