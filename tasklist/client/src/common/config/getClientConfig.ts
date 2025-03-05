/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';

const ClientConfigSchema = z.object({
  isEnterprise: z.boolean().optional().default(false),
  contextPath: z.string().optional().default('/'),
  baseName: z.string().optional().default('/'),
  canLogout: z.boolean().optional().default(true),
  isLoginDelegated: z.boolean().optional().default(false),
  organizationId: z.string().nullable().optional().default(null),
  clusterId: z.string().nullable().optional().default(null),
  mixpanelToken: z.string().nullable().optional().default(null),
  mixpanelAPIHost: z.string().nullable().optional().default(null),
  isResourcePermissionsEnabled: z.boolean().nullable().optional().default(null),
  isMultiTenancyEnabled: z.boolean().optional().default(false),
  maxRequestSize: z
    .number()
    .optional()
    .default(4 * 1024 * 1024),
});

const DEFAULT_CLIENT_CONFIG = ClientConfigSchema.safeParse({}).data!;

function getClientConfig() {
  if (typeof window === 'undefined') {
    return DEFAULT_CLIENT_CONFIG;
  }

  const {success, data} = ClientConfigSchema.safeParse(
    window?.clientConfig ?? {},
  );

  if (success) {
    return data;
  }

  return DEFAULT_CLIENT_CONFIG;
}

export {getClientConfig};
