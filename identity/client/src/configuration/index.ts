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

const adminPath = "/admin";

const apiBaseUrl = "/v2";

const loginApiUrl = "/login";

const logoutApiUrl = "/logout";

export const isOIDC = getClientConfigBoolean("isOidc", false);
export const isCamundaGroupsEnabled = getClientConfigBoolean(
  "isCamundaGroupsEnabled",
  true,
);
export const isTenantsApiEnabled = getClientConfigBoolean(
  "isTenantsApiEnabled",
  false,
);

export const docsUrl = "https://docs.camunda.io";

export const isSaaS = Boolean(getClientConfigString("organizationId"));

export function getApiBaseUrl() {
  return getBasePathBeforeAdmin() + apiBaseUrl;
}

export function getLoginApiUrl() {
  return getBasePathBeforeAdmin() + loginApiUrl;
}

export function getLogoutApiUrl() {
  return getBasePathBeforeAdmin() + logoutApiUrl;
}

export function getBaseUrl() {
  return getBasePathBeforeAdmin() + adminPath;
}

export function getBasePathBeforeAdmin(): string {
  const uiPath = window.location.pathname;
  const endIndex = uiPath.indexOf(adminPath);
  return uiPath.substring(0, endIndex);
}
