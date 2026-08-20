/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {APIRequestContext} from '@playwright/test';
import {jsonHeaders, buildUrl} from './http';

export async function cleanupGlobalClusterVariables(
  request: APIRequestContext,
  variableNames: string[],
): Promise<void> {
  if (variableNames.length === 0) return;

  console.log(
    `Cleaning up ${variableNames.length} global cluster variables via API...`,
  );

  await Promise.allSettled(
    variableNames.map(async (name) => {
      try {
        const response = await request.delete(
          buildUrl('/cluster-variables/global/{name}', {name}),
          {headers: jsonHeaders()},
        );
        if (response.status() === 204) {
          console.log(`Successfully deleted global cluster variable: ${name}`);
        } else if (response.status() === 404) {
          console.log(
            `Global cluster variable already deleted or doesn't exist: ${name}`,
          );
        } else {
          console.warn(
            `Unexpected response status ${response.status()} for global cluster variable ${name}`,
          );
        }
      } catch {}
    }),
  );
}

export async function cleanupTenantClusterVariables(
  request: APIRequestContext,
  variables: {tenantId: string; name: string}[],
): Promise<void> {
  if (variables.length === 0) return;

  console.log(
    `Cleaning up ${variables.length} tenant cluster variables via API...`,
  );

  await Promise.allSettled(
    variables.map(async ({tenantId, name}) => {
      try {
        const response = await request.delete(
          buildUrl('/cluster-variables/tenants/{tenantId}/{name}', {
            tenantId,
            name,
          }),
          {headers: jsonHeaders()},
        );
        if (response.status() === 204) {
          console.log(
            `Successfully deleted tenant cluster variable: ${tenantId}/${name}`,
          );
        } else if (response.status() === 404) {
          console.log(
            `Tenant cluster variable already deleted or doesn't exist: ${tenantId}/${name}`,
          );
        } else {
          console.warn(
            `Unexpected response status ${response.status()} for tenant cluster variable ${tenantId}/${name}`,
          );
        }
      } catch {}
    }),
  );
}
