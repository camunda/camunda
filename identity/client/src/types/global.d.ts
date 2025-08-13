/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

interface ClientConfig {
  VITE_IS_OIDC?: string;
  VITE_CAMUNDA_GROUPS_ENABLED?: string;
  VITE_TENANTS_API_ENABLED?: string;
  organizationId?: string;
  clusterId?: string;
}

export declare global {
  interface Window {
    clientConfig?: ClientConfig;
  }
}
