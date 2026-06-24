/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
	IncidentProcessInstanceStatisticsByError,
	IncidentProcessInstanceStatisticsByDefinition,
} from '@camunda/camunda-api-zod-schemas/8.10';

function createIncidentProcessInstanceStatisticsByError(
	overrides?: Partial<IncidentProcessInstanceStatisticsByError>,
): IncidentProcessInstanceStatisticsByError {
	return {
		errorHashCode: 1,
		errorMessage: 'An error occurred',
		activeInstancesWithErrorCount: 1,
		...overrides,
	};
}

function createIncidentProcessInstanceStatisticsByDefinition(
	overrides?: Partial<IncidentProcessInstanceStatisticsByDefinition>,
): IncidentProcessInstanceStatisticsByDefinition {
	return {
		processDefinitionId: 'process',
		processDefinitionKey: 'process-key',
		processDefinitionName: 'My Process',
		processDefinitionVersion: 1,
		tenantId: '<default>',
		activeInstancesWithErrorCount: 1,
		...overrides,
	};
}

export {createIncidentProcessInstanceStatisticsByError, createIncidentProcessInstanceStatisticsByDefinition};
