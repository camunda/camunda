/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { ApiDefinition, apiGet } from "src/utility/api/request.ts";

export interface TenantInfo {
  tenantId: string;
  name: string;
}

export interface C8Link {
  name: string;
  link: string;
}

export interface CamundaUser {
  userId: string;
  userKey: number;
  displayName: string;
  email: string;
  authorizedApplications: readonly string[];
  tenants: readonly TenantInfo[];
  groups: readonly string[];
  roles: readonly string[];
  salesPlanType: string;
  c8Links: readonly C8Link[];
  canLogout: boolean;
  apiUser: boolean;
}

export const getAuthentication: ApiDefinition<CamundaUser> = () =>
  apiGet("/authentication/me");

export const getSaasUserToken: ApiDefinition<string> = () =>
  apiGet("/authentication/me/token");
