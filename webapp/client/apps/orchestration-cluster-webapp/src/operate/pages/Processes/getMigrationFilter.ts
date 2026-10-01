/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {CreateMigrationBatchOperationRequestBody} from '@camunda/camunda-api-zod-schemas/8.11';
import {buildInstanceKeyCriterion} from '#/operate/shared/utils/buildInstanceKeyCriterion';
import {mapProcessInstancesFilter, type ProcessesSearch} from './processesFilter';
import {getActiveInstancesFilter} from './getActiveInstancesFilter';

function getMigrationFilter({
	search,
	includeIds,
	excludeIds,
	processDefinitionKey,
}: {
	search: ProcessesSearch;
	includeIds: string[];
	excludeIds: string[];
	processDefinitionKey: string;
}): CreateMigrationBatchOperationRequestBody['filter'] | null {
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
		processDefinitionKey: {$eq: processDefinitionKey},
	};
}

type MigrationScope = {
	filter: CreateMigrationBatchOperationRequestBody['filter'];
	selectedCount: number;
	isCountTruncated: boolean;
};

export {getMigrationFilter};
export type {MigrationScope};
