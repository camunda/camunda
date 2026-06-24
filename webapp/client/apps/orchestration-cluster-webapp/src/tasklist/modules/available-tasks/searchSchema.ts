/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {userTaskSchema} from '@camunda/camunda-api-zod-schemas/8.10';
import type {SearchMiddleware} from '@tanstack/react-router';
import {z} from 'zod';

const FILTER_VALUES = ['all-open', 'assigned-to-me', 'unassigned', 'completed'] as const;
type BuiltInFilter = (typeof FILTER_VALUES)[number];

function isBuiltInFilter(filter: string): filter is BuiltInFilter {
	// casting is necessary for the type guard to work
	return (FILTER_VALUES as readonly string[]).includes(filter);
}

type CustomFilterParam =
	| 'state'
	| 'assigned'
	| 'assignee'
	| 'candidateGroup'
	| 'processDefinitionKey'
	| 'tenantId'
	| 'dueDateFrom'
	| 'dueDateTo'
	| 'followUpDateFrom'
	| 'followUpDateTo'
	| 'elementId';

const tasklistIndexSearchSchema = z.object({
	filter: z.string().default('all-open'),
	sortBy: z.enum(['creation', 'follow-up', 'due', 'completion', 'priority']).default('creation'),
	state: userTaskSchema.shape.state.optional(),
	assigned: z.string().optional(),
	assignee: z.string().optional(),
	candidateGroup: z.string().optional(),
	processDefinitionKey: z.string().optional(),
	tenantId: z.string().optional(),
	dueDateFrom: z.string().optional(),
	dueDateTo: z.string().optional(),
	followUpDateFrom: z.string().optional(),
	followUpDateTo: z.string().optional(),
	elementId: z.string().optional(),
});

type TasklistIndexSearch = z.infer<typeof tasklistIndexSearchSchema>;

type CustomFilterSearchParams = Partial<Pick<TasklistIndexSearch, CustomFilterParam>>;

const tasklistIndexSearchDefaults = {
	filter: 'all-open',
	sortBy: 'creation',
} as const satisfies TasklistIndexSearch;

const enforceSortInvariant: SearchMiddleware<TasklistIndexSearch> = ({search, next}) => {
	const result = next(search);
	const completionEligible = result.filter === 'completed' || result.filter === 'custom';

	if (!completionEligible && result.sortBy === 'completion') {
		return {...result, sortBy: 'creation'};
	}

	return result;
};

const stripCustomFilterParams: SearchMiddleware<TasklistIndexSearch> = ({search, next}) => {
	const result = next(search);

	if (isBuiltInFilter(result.filter)) {
		return {
			...result,
			state: undefined,
			assigned: undefined,
			assignee: undefined,
			candidateGroup: undefined,
			processDefinitionKey: undefined,
			tenantId: undefined,
			dueDateFrom: undefined,
			dueDateTo: undefined,
			followUpDateFrom: undefined,
			followUpDateTo: undefined,
			elementId: undefined,
		};
	}

	return result;
};

export {
	tasklistIndexSearchSchema,
	tasklistIndexSearchDefaults,
	enforceSortInvariant,
	stripCustomFilterParams,
	FILTER_VALUES,
	isBuiltInFilter,
	type TasklistIndexSearch,
	type CustomFilterSearchParams,
	type BuiltInFilter,
};
