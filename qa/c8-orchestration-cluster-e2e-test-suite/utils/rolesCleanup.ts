/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {APIRequestContext} from '@playwright/test';
import {jsonHeaders, buildUrl} from './http';

export async function cleanupRoles(
  request: APIRequestContext,
  roleIds: string[],
): Promise<void> {
  if (roleIds.length === 0) return;

  console.log(`Cleaning up ${roleIds.length} roles via API...`);

  const results = await Promise.allSettled(
    roleIds.map(async (roleId) => {
      try {
        const response = await request.delete(
          buildUrl('/roles/{roleId}', {roleId}),
          {headers: jsonHeaders()},
        );
        if (response.status() === 204) {
          console.log(`Successfully deleted role: ${roleId}`);
          return {roleId, status: 'deleted'};
        } else if (response.status() === 404) {
          console.log(`Role already deleted or doesn't exist: ${roleId}`);
          return {roleId, status: 'not_found'};
        } else {
          console.warn(
            `Unexpected response status ${response.status()} for role ${roleId}`,
          );
          return {roleId, status: 'error', statusCode: response.status()};
        }
      } catch (error) {
        console.error(`Failed to delete role ${roleId}:`, error);
        return {roleId, status: 'failed', error};
      }
    }),
  );

  const failed = results.filter(
    (result) =>
      result.status === 'rejected' ||
      (result.status === 'fulfilled' && result.value.status === 'failed'),
  );

  if (failed.length > 0) {
    console.warn(`Failed to clean up ${failed.length} roles`);
  }
}
