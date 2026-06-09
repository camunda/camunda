/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {CurrentUser} from '@camunda/camunda-api-zod-schemas/8.10';

function createCurrentUser(overrides?: Partial<CurrentUser>): CurrentUser {
	return {
		username: 'demo',
		displayName: 'Demo User',
		email: 'demo@camunda.com',
		salesPlanType: null,
		authorizedComponents: ['*'],
		roles: [],
		c8Links: {},
		tenants: [],
		groups: [],
		canLogout: true,
		...overrides,
	};
}

export {createCurrentUser};
