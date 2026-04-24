/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {APIRequestContext} from '@playwright/test';
import {jsonHeaders, buildUrl} from './http';

export async function cleanupGlobalTaskListeners(
  request: APIRequestContext,
  ids: string[],
): Promise<void> {
  if (ids.length === 0) return;

  await Promise.allSettled(
    ids.map(async (id) => {
      try {
        await request.delete(buildUrl('/global-task-listeners/{id}', {id}), {
          headers: jsonHeaders(),
        });
      } catch {}
    }),
  );
}
