/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page} from '@playwright/test';

export async function deleteOrganization(
  page: Page,
  uuid: string,
): Promise<void> {
  console.log(`Deleting organization with UUID: ${uuid}`);

  const tokenResponse = await page.request.post(
    'https://weblogin.cloud.ultrawombat.com/oauth/token',
    {
      headers: {
        'Content-Type': 'application/json',
      },
      data: {
        grant_type: 'client_credentials',
        audience: 'cloud.ultrawombat.com',
        client_id: process.env.EXTERNAL_CONSOLE_API_CLIENT_ID!,
        client_secret: process.env.EXTERNAL_CONSOLE_API_CLIENT_SECRET!,
      },
    },
  );

  if (tokenResponse.status() !== 200) {
    const errorBody = await tokenResponse.text();
    throw new Error(`Failed to fetch access token. Response: ${errorBody}`);
  }

  const tokenJson = await tokenResponse.json();
  const token = tokenJson.access_token;
  console.log('Access Token:', token);

  const deleteUrl = `https://accounts.cloud.ultrawombat.com/external/qa/organizations/${uuid}`;
  console.log(`Delete URL: ${deleteUrl}`);

  const deleteResponse = await page.request.delete(deleteUrl, {
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
  });

  if (deleteResponse.status() !== 200) {
    const errorBody = await deleteResponse.text();
    throw new Error(
      `Failed to delete organization. Status: ${deleteResponse.status()}. Response: ${errorBody}`,
    );
  }

  console.log(`Organization with UUID ${uuid} deleted successfully.`);
}
