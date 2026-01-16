/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  getClientConfigBoolean,
  getClientConfigString,
} from "src/configuration/clientConfig";

const identityPath = "/identity";

const apiBaseUrl = "/v2";

const loginApiUrl = "/login";

const logoutApiUrl = "/logout";

export const isOIDC = getClientConfigBoolean("isOidc", false);
export const isLogoutCorsEnabled = getClientConfigBoolean(
  "isLogoutCorsEnabled",
  true,
);
export const isCamundaGroupsEnabled = getClientConfigBoolean(
  "isCamundaGroupsEnabled",
  true,
);
export const isTenantsApiEnabled = getClientConfigBoolean(
  "isTenantsApiEnabled",
  false,
);

// Will be removed with https://github.com/camunda/camunda/issues/40370
export const isUserTaskAuthorizationEnabled =
  window.localStorage.getItem("enableUserTaskAuthorization") === "true";

export const docsUrl = "https://docs.camunda.io";

export const isSaaS = Boolean(getClientConfigString("organizationId"));

export function getApiBaseUrl() {
  return getBasePathBeforeIdentity() + apiBaseUrl;
}

export function getLoginApiUrl() {
  return getBasePathBeforeIdentity() + loginApiUrl;
}

export function getLogoutApiUrl() {
  return getBasePathBeforeIdentity() + logoutApiUrl;
}

export function getBaseUrl() {
  return getBasePathBeforeIdentity() + identityPath;
}

export function getBasePathBeforeIdentity(): string {
  const uiPath = window.location.pathname;
  const endIndex = uiPath.indexOf(identityPath);
  return uiPath.substring(0, endIndex);
}
