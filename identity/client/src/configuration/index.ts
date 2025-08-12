/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { getEnvBoolean } from "src/configuration/env";

const identityPath = "/identity";

const apiBaseUrl = "/v2";

const loginApiUrl = "/login";

export const isOIDC = getEnvBoolean("IS_OIDC", false);
export const isCamundaGroupsEnabled = getEnvBoolean(
  "CAMUNDA_GROUPS_ENABLED",
  true,
);
export const isTenantsApiEnabled = getEnvBoolean("TENANTS_API_ENABLED", false);

export const docsUrl = "https://docs.camunda.io";

export const isSaaS = Boolean(window.clientConfig?.organizationId);

export function getApiBaseUrl() {
  return getBasePathBeforeIdentity() + apiBaseUrl;
}

export function getLoginApiUrl() {
  return getBasePathBeforeIdentity() + loginApiUrl;
}

export function getBaseUrl() {
  return getBasePathBeforeIdentity() + identityPath;
}

export function getBasePathBeforeIdentity(): string {
  const uiPath = window.location.pathname;
  const endIndex = uiPath.indexOf(identityPath);
  return uiPath.substring(0, endIndex);
}
