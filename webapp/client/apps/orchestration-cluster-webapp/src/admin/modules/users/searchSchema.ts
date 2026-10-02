/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import type {QueryUsersRequestBody} from '@camunda/camunda-api-zod-schemas/8.11';

const PAGE_SIZES = [10, 20, 50, 100] as const;
const DEFAULT_PAGE_SIZE = 20;

const SORTABLE_FIELDS = ['username', 'name', 'email'] as const;

const usersSearchSchema = z.object({
	search: z.coerce.string().optional(),
	sortField: z.enum(SORTABLE_FIELDS).optional(),
	sortOrder: z.enum(['asc', 'desc']).optional(),
	page: z.number().int().positive().optional(),
	pageSize: z.literal(PAGE_SIZES).optional(),
});

type UsersSearch = z.infer<typeof usersSearchSchema>;

function getUsersRequestBody(search: UsersSearch): QueryUsersRequestBody {
	const pageSize = search.pageSize ?? DEFAULT_PAGE_SIZE;
	const searchTerm = search.search?.trim();

	return {
		sort: [{field: search.sortField ?? 'username', order: search.sortOrder ?? 'asc'}],
		filter: searchTerm === undefined || searchTerm === '' ? {} : {username: {$like: `*${searchTerm}*`}},
		page: {
			from: ((search.page ?? 1) - 1) * pageSize,
			limit: pageSize,
		},
	};
}

export {DEFAULT_PAGE_SIZE, PAGE_SIZES, usersSearchSchema, getUsersRequestBody};
export type {UsersSearch};
