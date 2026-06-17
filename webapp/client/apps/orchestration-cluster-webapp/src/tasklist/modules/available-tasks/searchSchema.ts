/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {SearchMiddleware} from '@tanstack/react-router';
import {z} from 'zod';

const FILTER_VALUES = ['all-open', 'assigned-to-me', 'unassigned', 'completed'] as const;

const tasklistIndexSearchSchema = z.object({
	filter: z.enum(FILTER_VALUES).default('all-open'),
	sortBy: z.enum(['creation', 'follow-up', 'due', 'completion', 'priority']).default('creation'),
});

type TasklistIndexSearch = z.infer<typeof tasklistIndexSearchSchema>;

const tasklistIndexSearchDefaults = {
	filter: 'all-open',
	sortBy: 'creation',
} as const satisfies TasklistIndexSearch;

const enforceSortInvariant: SearchMiddleware<TasklistIndexSearch> = ({search, next}) => {
	const result = next(search);
	return result.filter !== 'completed' && result.sortBy === 'completion' ? {...result, sortBy: 'creation'} : result;
};

export {
	tasklistIndexSearchSchema,
	tasklistIndexSearchDefaults,
	enforceSortInvariant,
	FILTER_VALUES,
	type TasklistIndexSearch,
};
