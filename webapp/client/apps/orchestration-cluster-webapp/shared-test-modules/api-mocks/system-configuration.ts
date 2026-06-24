/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {GetSystemConfigurationResponseBody} from '@camunda/camunda-api-zod-schemas/8.10';

function createSystemConfiguration(
	overrides?: Partial<GetSystemConfigurationResponseBody>,
): GetSystemConfigurationResponseBody {
	return {
		jobMetrics: {
			enabled: false,
			exportInterval: 'PT10S',
			maxWorkerNameLength: 128,
			maxJobTypeLength: 256,
			maxTenantIdLength: 64,
			maxUniqueKeys: 100,
		},
		components: {active: []},
		deployment: {
			isMultiTenancyEnabled: false,
			maxRequestSize: 0,
		},
		authentication: {canLogout: true, isLoginDelegated: false},
		cloud: {
			stage: null,
		},
		...overrides,
	};
}

export {createSystemConfiguration};
