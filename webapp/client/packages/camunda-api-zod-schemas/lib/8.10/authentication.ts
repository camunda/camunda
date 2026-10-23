/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {API_VERSION, type Endpoint} from './common';

const currentUserSchema = z.object({
	// An unprotected API answers a caller without an identity with `anonymous: true` and a `null`
	// username instead of a 401. Earlier 8.10 patch releases do not send the flag.
	anonymous: z.boolean().optional(),
	username: z.string().nullable(),
	displayName: z.string().nullable(),
	email: z.string().nullable(),
	authorizedComponents: z.array(z.string()),
	tenants: z.array(
		z.object({
			tenantId: z.string(),
			name: z.string(),
			description: z.string().nullable(),
		}),
	),
	groups: z.array(z.string()),
	roles: z.array(z.string()),
	salesPlanType: z.string().nullable(),
	c8Links: z.record(z.string(), z.string()),
	canLogout: z.boolean(),
});

type CurrentUser = z.infer<typeof currentUserSchema>;

const getCurrentUser = {
	method: 'GET',
	getUrl: () => `/${API_VERSION}/authentication/me` as const,
} as const satisfies Endpoint;

export {currentUserSchema, getCurrentUser};
export type {CurrentUser};
