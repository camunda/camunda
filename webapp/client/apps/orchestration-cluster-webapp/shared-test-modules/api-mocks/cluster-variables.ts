/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
	ClusterVariable,
	ClusterVariableSearchResult,
	QueryClusterVariablesResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.11';
import {createPaginatedResponse} from './shared';

function createClusterVariable(overrides?: Partial<ClusterVariable>): ClusterVariable {
	return {
		name: 'my-variable',
		scope: 'GLOBAL',
		tenantId: null,
		value: '"my value"',
		...overrides,
	};
}

function createClusterVariableSearchResult(
	overrides?: Partial<ClusterVariableSearchResult>,
): ClusterVariableSearchResult {
	return {...createClusterVariable(), isTruncated: false, ...overrides};
}

function createQueryClusterVariablesResponse(
	items: ClusterVariableSearchResult[] = [],
): QueryClusterVariablesResponseBody {
	return createPaginatedResponse({
		items,
		page: {totalItems: items.length, startCursor: null, endCursor: null, hasMoreTotalItems: false},
	});
}

export {createClusterVariable, createClusterVariableSearchResult, createQueryClusterVariablesResponse};
