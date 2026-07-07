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
type TaskDetailsHistorySortField = TaskDetailsHistorySortParams['sortBy'];

const sortSearchValueCodec = z.codec(z.string(), sortSchema, {
	decode: (sort, ctx) => {
		const [sortBy, sortOrder, ...rest] = sort.split('+');

		if (rest.length > 0) {
			ctx.issues.push({code: 'custom', message: 'Invalid sort search value', input: sort});
			return z.NEVER;
		}

		const result = sortSchema.safeParse({sortBy, sortOrder});
		if (!result.success) {
			ctx.issues.push({code: 'custom', message: 'Invalid sort search value', input: sort});
			return z.NEVER;
		}

		return result.data;
	},
	encode: ({sortBy, sortOrder}) => `${sortBy}+${sortOrder}`,
});

function normalizeSortSearchValue(sort?: string) {
	if (sort === undefined) {
		return DEFAULT_SORT_SEARCH_VALUE;
	}

	const sortParams = sortSearchValueCodec.safeDecode(sort);
	return sortParams.success ? sortSearchValueCodec.encode(sortParams.data) : DEFAULT_SORT_SEARCH_VALUE;
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
	const sortParams = sortSearchValueCodec.safeDecode(search.sort);
	return sortParams.success ? sortParams.data : DEFAULT_SORT_PARAMS;
}

function getAuditLogSort(search: TaskDetailsHistorySearch): TaskDetailsHistorySort {
	const sort = getSortParams(search);

	return {
		field: sort.sortBy,
		order: sort.sortOrder,
	};
}

function getNextSortSearchValue(sortBy: TaskDetailsHistorySortField, currentSortOrder?: 'asc' | 'desc') {
	return sortSearchValueCodec.encode({sortBy, sortOrder: currentSortOrder === 'asc' ? 'desc' : 'asc'});
}

export {
	getAuditLogSort,
	getNextSortSearchValue,
	getSortParams,
	taskDetailsHistorySearchDefaults,
	taskDetailsHistorySearchSchema,
	type TaskDetailsHistorySearch,
	type TaskDetailsHistorySort,
	type TaskDetailsHistorySortField,
};
