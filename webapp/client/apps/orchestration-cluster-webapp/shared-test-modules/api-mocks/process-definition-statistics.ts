/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
	ProcessDefinitionInstanceStatistics,
	ProcessDefinitionInstanceVersionStatistics,
} from '@camunda/camunda-api-zod-schemas/8.10';

function createProcessDefinitionInstanceStatistics(
	overrides?: Partial<ProcessDefinitionInstanceStatistics>,
): ProcessDefinitionInstanceStatistics {
	return {
		processDefinitionId: 'process',
		latestProcessDefinitionName: 'My Process',
		hasMultipleVersions: false,
		activeInstancesWithoutIncidentCount: 0,
		activeInstancesWithIncidentCount: 0,
		tenantId: '<default>',
		...overrides,
	};
}

function createProcessDefinitionInstanceVersionStatistics(
	overrides?: Partial<ProcessDefinitionInstanceVersionStatistics>,
): ProcessDefinitionInstanceVersionStatistics {
	return {
		processDefinitionId: 'process',
		processDefinitionKey: 'process-key',
		processDefinitionName: 'My Process',
		processDefinitionVersion: 1,
		activeInstancesWithoutIncidentCount: 0,
		activeInstancesWithIncidentCount: 0,
		tenantId: '<default>',
		...overrides,
	};
}

export {createProcessDefinitionInstanceStatistics, createProcessDefinitionInstanceVersionStatistics};
