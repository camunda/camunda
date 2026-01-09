/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {APIRequestContext} from 'playwright-core';
import {Serializable} from 'playwright-core/types/structs';
import {assertRequiredFields, buildUrl, jsonHeaders} from '../http';
import {expect} from '@playwright/test';
import {authorizedComponentRequiredFields} from '../beans/requestBeans';

export async function createComponentAuthorization(
  request: APIRequestContext,
  body: Serializable,
) {
  const res = await request.post(buildUrl('/authorizations'), {
    headers: jsonHeaders(),
    data: body,
  });

  expect(res.status()).toBe(201);
  const json = await res.json();
  assertRequiredFields(json, authorizedComponentRequiredFields);
}

export async function grantUserResourceAuthorization(
  request: APIRequestContext,
  user: {username: string; password: string; name: string; email: string},
) {
  const USER_RESOURCE_AUTHORIZATION = {
    ownerId: user.username,
    ownerType: 'USER',
    resourceId: '*',
    resourceType: 'RESOURCE',
    permissionTypes: ['READ'],
  };

  const authRes = await request.post(buildUrl('/authorizations'), {
    headers: jsonHeaders(),
    data: USER_RESOURCE_AUTHORIZATION,
  });
  expect(authRes.status()).toBe(201);
  const authBody = await authRes.json();
  assertRequiredFields(authBody, ['authorizationKey']);
  return {
    authorizationKey: authBody.authorizationKey,
  };
}

export async function cleanupAuthorizations(
  request: APIRequestContext,
  authorizationKeys: string[],
): Promise<void> {
  if (authorizationKeys.length === 0) return;

  console.log(
    `Cleaning up ${authorizationKeys.length} authorizations via API...`,
  );

  await Promise.allSettled(
    authorizationKeys.map(async (authorizationKey) => {
      try {
        const response = await request.delete(
          buildUrl('/authorizations/{authorizationKey}', {
            authorizationKey: authorizationKey,
          }),
          {headers: jsonHeaders()},
        );
        if (response.status() === 204) {
          console.log(
            `Successfully deleted an authorization: ${authorizationKey}`,
          );
        } else if (response.status() === 404) {
          console.log(
            `Authorization already deleted or doesn't exist: ${authorizationKey}`,
          );
        } else {
          console.warn(
            `Unexpected response status ${response.status()} for authorization ${authorizationKey}`,
          );
        }
      } catch {}
    }),
  );
}