/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda
 * Services GmbH under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file except in compliance with the Camunda License 1.0.
 */

import { getEnvBoolean } from "src/configuration/env";

const baseUrl = "/identity";

const apiBaseUrl = "/v2";

export const isOIDC = getEnvBoolean("IS_OIDC");

export const docsUrl = "https://docs.camunda.io";

export function getApiBaseUrl() {
  return getBaseUrl(apiBaseUrl);
}

export function getBaseUrl(path = baseUrl) {
  const uiPath = window.location.pathname;
  const endIndex = uiPath.indexOf(baseUrl);
  return uiPath.substring(0, endIndex) + path;
}
