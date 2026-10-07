/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {linkOptions} from '@tanstack/react-router';
import {
	processInstanceSchema,
	type ProcessDefinition,
	type ProcessInstance,
} from '@camunda/camunda-api-zod-schemas/8.11';
import {getClientConfig} from '#/shared/config/getClientConfig';
import {getMigrationFilter, getMigrationStatisticsFilter, type MigrationScope} from './getMigrationFilter';
import type {ProcessesSearch} from './processesFilter';

const instanceMigrationSchema = processInstanceSchema.pick({
	processInstanceKey: true,
	processDefinitionKey: true,
	processDefinitionId: true,
	processDefinitionName: true,
	processDefinitionVersion: true,
	processDefinitionVersionTag: true,
	tenantId: true,
});

function getSourceSearch({
	processDefinitionId,
	processDefinitionVersion,
	tenantId,
}: Pick<ProcessInstance, 'processDefinitionId' | 'processDefinitionVersion' | 'tenantId'>) {
	return {
		active: true,
		incidents: true,
		suspended: true,
		completed: false,
		canceled: false,
		process: processDefinitionId,
		version: processDefinitionVersion,
		tenantId: getClientConfig().deployment.isMultiTenancyEnabled ? tenantId : undefined,
	} satisfies ProcessesSearch;
}

function getInstanceMigrationLocation(processInstance: ProcessInstance) {
	return linkOptions({
		to: '/operate/processes',
		search: getSourceSearch(processInstance),
		state: {
			operateInstanceMigration: {
				processInstanceKey: processInstance.processInstanceKey,
				processDefinitionKey: processInstance.processDefinitionKey,
				processDefinitionId: processInstance.processDefinitionId,
				processDefinitionName: processInstance.processDefinitionName,
				processDefinitionVersion: processInstance.processDefinitionVersion,
				processDefinitionVersionTag: processInstance.processDefinitionVersionTag,
				tenantId: processInstance.tenantId,
			},
		},
	});
}

function getInstanceMigration(state: unknown): {source: ProcessDefinition; scope: MigrationScope} | null {
	const result = instanceMigrationSchema.safeParse(state);
	if (!result.success) {
		return null;
	}
	const instance = result.data;
	const source: ProcessDefinition = {
		processDefinitionKey: instance.processDefinitionKey,
		processDefinitionId: instance.processDefinitionId,
		name: instance.processDefinitionName,
		version: instance.processDefinitionVersion,
		versionTag: instance.processDefinitionVersionTag,
		tenantId: instance.tenantId,
		resourceName: null,
		hasStartForm: false,
		state: 'ACTIVE',
	};
	const selection = {search: getSourceSearch(instance), includeIds: [instance.processInstanceKey], excludeIds: []};
	const filter = getMigrationFilter({...selection, processDefinitionKey: source.processDefinitionKey});

	return filter === null
		? null
		: {
				source,
				scope: {
					filter,
					statisticsFilter: getMigrationStatisticsFilter(selection),
					selectedCount: 1,
					isCountTruncated: false,
				},
			};
}

export {getInstanceMigration, getInstanceMigrationLocation};
