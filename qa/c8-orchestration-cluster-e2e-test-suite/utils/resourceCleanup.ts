/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {APIRequestContext} from '@playwright/test';
import {defaultHeaders, buildUrl} from './http';

export async function cleanupResources(
  request: APIRequestContext,
  resourceKeys: string[],
): Promise<void> {
  if (resourceKeys.length === 0) return;

  console.log(`Cleaning up ${resourceKeys.length} resources via API...`);

  const results = await Promise.allSettled(
    resourceKeys.map(async (resourceKey) => {
      try {
        const response = await request.post(
          buildUrl('/resources/{resourceKey}/deletion', {resourceKey}),
          {headers: defaultHeaders()},
        );
        if (response.status() === 200) {
          console.log(`Successfully deleted resource: ${resourceKey}`);
          return {resourceKey, status: 'deleted'};
        } else if (response.status() === 404) {
          console.log(
            `Resource already deleted or doesn't exist: ${resourceKey}`,
          );
          return {resourceKey, status: 'not_found'};
        } else {
          console.warn(
            `Unexpected response status ${response.status()} for resource ${resourceKey}`,
          );
          return {
            resourceKey,
            status: 'error',
            statusCode: response.status(),
          };
        }
      } catch (error) {
        console.error(`Failed to delete resource ${resourceKey}:`, error);
        return {resourceKey, status: 'failed', error};
      }
    }),
  );

  const failed = results.filter(
    (result) =>
      result.status === 'rejected' ||
      (result.status === 'fulfilled' && result.value.status === 'failed'),
  );

  if (failed.length > 0) {
    console.warn(`Failed to clean up ${failed.length} resources`);
  }
}
