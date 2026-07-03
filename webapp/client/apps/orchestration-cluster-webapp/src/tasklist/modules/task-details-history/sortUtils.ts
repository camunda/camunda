/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import type {AuditLogSortField} from '@camunda/camunda-api-zod-schemas/8.10';

const INITIAL_SORT_ORDER = 'desc';
const DEFAULT_SORT_PARAMS = {
	sortBy: 'timestamp',
	sortOrder: INITIAL_SORT_ORDER,
} as const;
const DEFAULT_SORT_SEARCH_VALUE = `${DEFAULT_SORT_PARAMS.sortBy}+${DEFAULT_SORT_PARAMS.sortOrder}`;
const taskDetailsHistorySearchDefaults = {
	sort: DEFAULT_SORT_SEARCH_VALUE,
};

const sortSchema = z.object({
	sortBy: z.enum(['timestamp', 'operationType', 'actorId']),
	sortOrder: z.enum(['asc', 'desc']),
});

type TaskDetailsHistorySortParams = z.infer<typeof sortSchema>;

function parseSortSearchValue(sort: string): TaskDetailsHistorySortParams | null {
	const [sortBy, sortOrder, ...rest] = sort.split('+');

	if (rest.length > 0) {
		return null;
	}

	const result = sortSchema.safeParse({sortBy, sortOrder});
	return result.success ? result.data : null;
}

function normalizeSortSearchValue(sort?: string) {
	if (sort === undefined) {
		return DEFAULT_SORT_SEARCH_VALUE;
	}

	const sortParams = parseSortSearchValue(sort);
	return sortParams === null ? DEFAULT_SORT_SEARCH_VALUE : `${sortParams.sortBy}+${sortParams.sortOrder}`;
}

const taskDetailsHistorySearchSchema = z.object({
	sort: z.string().optional().transform(normalizeSortSearchValue),
});

type TaskDetailsHistorySort = {
	field: AuditLogSortField;
	order: 'asc' | 'desc';
};
type TaskDetailsHistorySearch = z.infer<typeof taskDetailsHistorySearchSchema>;

function getSortParams(search: TaskDetailsHistorySearch): TaskDetailsHistorySortParams {
	return parseSortSearchValue(search.sort) ?? DEFAULT_SORT_PARAMS;
}

function getAuditLogSort(search: TaskDetailsHistorySearch): TaskDetailsHistorySort {
	const sort = getSortParams(search);

	return {
		field: sort.sortBy,
		order: sort.sortOrder,
	};
}

function getSortSearchValue(sortKey: string, currentSortOrder?: 'asc' | 'desc') {
	return `${sortKey}+${currentSortOrder === 'asc' ? 'desc' : 'asc'}`;
}

export {
	getAuditLogSort,
	getSortParams,
	getSortSearchValue,
	taskDetailsHistorySearchDefaults,
	taskDetailsHistorySearchSchema,
	type TaskDetailsHistorySearch,
	type TaskDetailsHistorySort,
};
