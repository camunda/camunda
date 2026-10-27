/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Group, QueryGroupsResponseBody} from '@camunda/camunda-api-zod-schemas/8.11';

function createGroup(overrides?: Partial<Group>): Group {
	return {
		groupId: 'engineering',
		name: 'Engineering',
		description: 'The engineering team',
		...overrides,
	};
}

function createPage<TItem>(items: TItem[], overrides?: {totalItems?: number}) {
	return {
		items,
		page: {
			totalItems: overrides?.totalItems ?? items.length,
			startCursor: null,
			endCursor: null,
			hasMoreTotalItems: false,
		},
	};
}

function createQueryGroupsResponse(overrides?: Partial<QueryGroupsResponseBody>): QueryGroupsResponseBody {
	const items = overrides?.items ?? [createGroup()];

	return {
		items,
		page: {
			totalItems: items.length,
			startCursor: null,
			endCursor: null,
			hasMoreTotalItems: false,
			...overrides?.page,
		},
	};
}

export {createGroup, createPage, createQueryGroupsResponse};
