/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  PermissionType,
  ResourceType,
} from "@camunda/camunda-api-zod-schemas/8.10";
import {
  getClientConfigBoolean,
  getClientConfigObject,
  getClientConfigString,
} from "src/configuration/clientConfig";

export const isOIDC = getClientConfigBoolean("isOidc", false);
export const isCamundaGroupsEnabled = getClientConfigBoolean(
  "isCamundaGroupsEnabled",
  true,
);
export const isTenantsApiEnabled = getClientConfigBoolean(
  "isTenantsApiEnabled",
  false,
);

export const docsUrl = "https://docs.camunda.io/docs/next";

export const isSaaS = Boolean(getClientConfigString("organizationId"));

export const resourcePermissions = getClientConfigObject(
  "resourcePermissions",
  {} as Record<ResourceType, PermissionType[]>,
) as Record<ResourceType, PermissionType[]>;

export const defaultRoleIds = getClientConfigObject<string[]>(
  "defaultRoleIds",
  [],
);
