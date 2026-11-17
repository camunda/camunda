/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {GetProcessDefinitionStatisticsRequestBody} from '@camunda/camunda-api-zod-schemas/8.11';
import {mapProcessInstancesFilter, type ProcessesSearch} from './processesFilter';

type StatisticsFilter = NonNullable<GetProcessDefinitionStatisticsRequestBody['filter']>;

/**
 * Mirrors legacy Operate's `useProcessInstanceStatisticsFilters`: the diagram statistics use the
 * instance list filter minus the process-definition fields, which the endpoint scopes by path.
 * Returns `undefined` when the instance list would skip its request, so the caller skips too.
 */
function getStatisticsFilter(search: ProcessesSearch): StatisticsFilter | undefined {
	const filter = mapProcessInstancesFilter(search);
	if (filter === undefined) {
		return undefined;
	}

	const {processDefinitionId, processDefinitionVersion, ...statisticsFilter} = filter;
	return statisticsFilter;
}

export {getStatisticsFilter};
