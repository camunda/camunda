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
  const uniqueIds = [...new Set(ids)];
  if (uniqueIds.length === 0) return;

  console.log(
    `Cleaning up ${uniqueIds.length} global task listeners via API...`,
  );

  const results = await Promise.allSettled(
    uniqueIds.map(async (id) => {
      try {
        const response = await request.delete(
          buildUrl('/global-task-listeners/{id}', {id}),
          {headers: jsonHeaders()},
        );
        if (response.status() === 204) {
          console.log(`Successfully deleted global task listener: ${id}`);
          return {id, status: 'deleted'};
        } else if (response.status() === 404) {
          console.log(
            `Global task listener already deleted or doesn't exist: ${id}`,
          );
          return {id, status: 'not_found'};
        } else {
          console.warn(
            `Unexpected response status ${response.status()} for global task listener ${id}`,
          );
          return {id, status: 'error', statusCode: response.status()};
        }
      } catch (error) {
        console.error(`Failed to delete global task listener ${id}:`, error);
        return {id, status: 'failed', error};
      }
    }),
  );

  const failed = results.filter(
    (result) =>
      result.status === 'rejected' ||
      (result.status === 'fulfilled' && result.value.status === 'failed'),
  );

  if (failed.length > 0) {
    console.warn(
      `Failed to clean up ${failed.length} global task listeners`,
    );
  }
}
