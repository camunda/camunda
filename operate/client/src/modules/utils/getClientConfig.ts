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
  organizationId: z.string().min(1).nullable().optional().default(null),
  clusterId: z.string().min(1).nullable().optional().default(null),
  mixpanelToken: z.string().nullable().optional().default(null),
  mixpanelAPIHost: z.string().nullable().optional().default(null),
  tasklistUrl: z.string().nullable().optional().default(null),
  resourcePermissionsEnabled: z.boolean().optional().default(false),
  multiTenancyEnabled: z.boolean().optional().default(false),
  databaseType: z.string().optional().default('document-store'),
});

const DEFAULT_CLIENT_CONFIG = ClientConfigSchema.safeParse({}).data!;

const getClientConfig = (() => {
  let cachedConfig: z.infer<typeof ClientConfigSchema> | undefined;

  return () => {
    if (cachedConfig !== undefined) {
      return cachedConfig;
    }

    if (typeof window === 'undefined') {
      return DEFAULT_CLIENT_CONFIG;
    }

    const {success, data} = ClientConfigSchema.safeParse(
      window?.clientConfig ?? {},
    );

    cachedConfig = success ? data : DEFAULT_CLIENT_CONFIG;
    return cachedConfig;
  };
})();

export {getClientConfig};
