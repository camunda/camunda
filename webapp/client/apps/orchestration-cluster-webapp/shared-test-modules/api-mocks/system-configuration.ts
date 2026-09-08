/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {GetSystemConfigurationResponseBody} from '@camunda/camunda-api-zod-schemas/8.10';

function createSystemConfiguration(
	overrides?: Omit<Partial<GetSystemConfigurationResponseBody>, 'deployment'> & {
		deployment?: Partial<GetSystemConfigurationResponseBody['deployment']>;
	},
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
		authentication: {canLogout: true, isLoginDelegated: false},
		cloud: {
			stage: null,
		},
		...overrides,
		deployment: {
			isMultiTenancyEnabled: false,
			isWaitStatesEnabled: true,
			maxRequestSize: 0,
			...overrides?.deployment,
		},
	};
}

export {createSystemConfiguration};
