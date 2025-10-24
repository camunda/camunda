/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {APIRequestContext, Page} from '@playwright/test';
import {jsonHeaders, buildUrl} from './http';

export async function cleanupGroups(
  request: APIRequestContext,
  groupIds: string[],
): Promise<void> {
  if (groupIds.length === 0) return;

  console.log(`Cleaning up ${groupIds.length} groups via API...`);

  await Promise.allSettled(
    groupIds.map(async (groupId) => {
      try {
        const response = await request.delete(
          buildUrl('/groups/{groupId}', {groupId}),
          {
            headers: jsonHeaders(),
          },
        );

        if (response.status() === 204) {
          console.log(`Successfully deleted group: ${groupId}`);
        } else if (response.status() === 404) {
          console.log(`Group already deleted or doesn't exist: ${groupId}`);
        } else {
          console.warn(
            `Unexpected response status ${response.status()} for group ${groupId}`,
          );
        }
      } catch {}
    }),
  );
}

export async function cleanupGroupsSafely(
  page: Page,
  groupIds: string[],
): Promise<void> {
  if (groupIds.length === 0) return;

  try {
    const request = page.request;
    await cleanupGroups(request, groupIds);
  } catch (error) {
    console.log('API cleanup failed:', error);
  }
}
