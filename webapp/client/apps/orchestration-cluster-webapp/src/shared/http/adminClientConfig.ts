/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';

const CONFIG_PREFIX = /^\s*window\.clientConfig\s*=\s*/;

const adminClientConfigSchema = z.object({
	idPattern: z.string().nullish(),
	resourcePermissions: z.record(z.string(), z.array(z.string())).default({}),
	defaultRoleIds: z.array(z.string()).default([]),
});

type AdminClientConfig = z.infer<typeof adminClientConfigSchema>;

// The Admin client config controller serves a script assigning `window.clientConfig`, not JSON.
function parseAdminClientConfig(script: string): AdminClientConfig {
	const json = script.replace(CONFIG_PREFIX, '').trim().replace(/;$/, '');
	return adminClientConfigSchema.parse(JSON.parse(json));
}

export {parseAdminClientConfig};
export type {AdminClientConfig};
