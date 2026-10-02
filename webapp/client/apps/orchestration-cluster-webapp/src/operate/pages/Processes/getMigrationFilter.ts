/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
	CreateMigrationBatchOperationRequestBody,
	GetProcessDefinitionStatisticsRequestBody,
} from '@camunda/camunda-api-zod-schemas/8.11';
import {buildInstanceKeyCriterion} from '#/operate/shared/utils/buildInstanceKeyCriterion';
import {mapProcessInstancesFilter, type ProcessesSearch} from './processesFilter';
import {getActiveInstancesFilter} from './getActiveInstancesFilter';

type SelectionParams = {
	search: ProcessesSearch;
	includeIds: string[];
	excludeIds: string[];
};

function getActiveSelectionFilter({search, includeIds, excludeIds}: SelectionParams) {
	const filter = getActiveInstancesFilter(mapProcessInstancesFilter(search) ?? {});
	if (filter === null) {
		return null;
	}
	const keyCriterion = buildInstanceKeyCriterion(includeIds, excludeIds);
	const baseKey = filter.processInstanceKey;
	const baseKeyCriterion = typeof baseKey === 'string' ? {$eq: baseKey} : baseKey;

	return {
		...filter,
		...(keyCriterion ? {processInstanceKey: {...baseKeyCriterion, ...keyCriterion}} : {}),
	};
}

function getMigrationFilter({
	processDefinitionKey,
	...selection
}: SelectionParams & {processDefinitionKey: string}): CreateMigrationBatchOperationRequestBody['filter'] | null {
	const filter = getActiveSelectionFilter(selection);
	return filter === null ? null : {...filter, processDefinitionKey: {$eq: processDefinitionKey}};
}

/** The summary counts exactly the instances the migration request covers. */
function getMigrationStatisticsFilter(selection: SelectionParams): GetProcessDefinitionStatisticsRequestBody['filter'] {
	const filter = getActiveSelectionFilter(selection);
	if (filter === null) {
		return {};
	}
	const {processDefinitionId, processDefinitionVersion, ...statisticsFilter} = filter;
	return statisticsFilter;
}

type MigrationScope = {
	filter: CreateMigrationBatchOperationRequestBody['filter'];
	statisticsFilter: GetProcessDefinitionStatisticsRequestBody['filter'];
	selectedCount: number;
};

export {getMigrationFilter, getMigrationStatisticsFilter};
export type {MigrationScope};
