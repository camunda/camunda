/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {ProblemDetails} from '@camunda/camunda-api-zod-schemas/8.11';

type PageInfo = {
	totalItems: number;
	startCursor: string | null;
	endCursor: string | null;
	hasMoreTotalItems: boolean;
};

function createPaginatedResponse<T>(overrides?: Partial<{items: T[]; page: PageInfo}>) {
	return {
		items: [] as T[],
		page: {totalItems: 0, startCursor: null, endCursor: null, hasMoreTotalItems: false},
		...overrides,
	};
}

function createProblemDetails(overrides?: Partial<ProblemDetails>): ProblemDetails {
	return {
		type: 'about:blank',
		title: 'ERROR',
		status: 500,
		detail: 'Something went wrong',
		instance: '/v2/user-tasks/2251799813685281/assignment',
		...overrides,
	};
}

type PageWith<T, K extends string> = T extends unknown ? (K extends keyof T ? T : never) : never;

function isOffsetPagination<T extends object>(page: T | undefined): page is PageWith<T, 'from'> {
	return page !== undefined && 'from' in page;
}

function isCursorForwardPagination<T extends object>(page: T | undefined): page is PageWith<T, 'after'> {
	return page !== undefined && 'after' in page;
}

export {createPaginatedResponse, createProblemDetails, isOffsetPagination, isCursorForwardPagination};
