/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {request} from '@playwright/test';

const KEYCLOAK_URL =
  process.env.KEYCLOAK_URL || 'http://localhost:18080/auth';

interface TokenResponse {
  access_token: string;
  expires_in: number;
  refresh_expires_in: number;
  token_type: string;
  scope?: string;
}

/**
 * Fetch an access token from Keycloak using client credentials flow.
 *
 * @param realm - The Keycloak realm name
 * @param clientId - The client ID
 * @param clientSecret - The client secret
 * @returns The access token string
 */
export async function getAccessToken(
  realm: string = 'camunda-platform',
  clientId: string = 'zeebe',
  clientSecret: string = 'zecobe-secret',
): Promise<string> {
  const tokenEndpoint = `${KEYCLOAK_URL}/realms/${realm}/protocol/openid-connect/token`;

  const apiRequestContext = await request.newContext();

  try {
    const response = await apiRequestContext.post(tokenEndpoint, {
      form: {
        grant_type: 'client_credentials',
        client_id: clientId,
        client_secret: clientSecret,
      },
    });

    if (!response.ok()) {
      const errorBody = await response.text();
      throw new Error(
        `Failed to fetch access token from Keycloak. Status: ${response.status()}, Response: ${errorBody}`,
      );
    }

    const tokenResponse: TokenResponse = await response.json();
    return tokenResponse.access_token;
  } catch (error: unknown) {
    const errorMessage =
      error instanceof Error ? error.message : 'Unknown error';
    throw new Error(
      `Keycloak token fetch failed: ${errorMessage}. ` +
        `Ensure Keycloak is running and the realm/client is configured correctly.`,
    );
  } finally {
    await apiRequestContext.dispose();
  }
}
