/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {API_VERSION, type Endpoint} from './common';
import {variableSearchQueryResultSchema} from './gen/zod/variableSearchQueryResultSchema';
import {variableSearchQuerySchema} from './gen/zod/variableSearchQuerySchema';
import {variableSearchResultSchema} from './gen/zod/variableSearchResultSchema';
import type {VariableSearchQuery} from './gen/types/VariableSearchQuery';
import type {VariableSearchQueryResult} from './gen/types/VariableSearchQueryResult';
import type {VariableSearchResult} from './gen/types/VariableSearchResult';

// The search result is used because it includes `isTruncated`. `GET /variables/{key}` returns `VariableResult`, which has no `isTruncated`.
const variableSchema = variableSearchResultSchema;
type Variable = VariableSearchResult;

const queryVariablesRequestBodySchema = variableSearchQuerySchema;
type QueryVariablesRequestBody = VariableSearchQuery;

const queryVariablesResponseBodySchema = variableSearchQueryResultSchema;
type QueryVariablesResponseBody = VariableSearchQueryResult;

const getVariable = {
	method: 'GET',
	getUrl: ({variableKey}) => `/${API_VERSION}/variables/${variableKey}` as const,
} as const satisfies Endpoint<Pick<Variable, 'variableKey'>>;

const queryVariables = {
	method: 'POST',
	getUrl: ({truncateValues} = {}) =>
		`/${API_VERSION}/variables/search${truncateValues !== undefined ? `?truncateValues=${truncateValues}` : ''}` as const,
} as const satisfies Endpoint<{truncateValues?: boolean}>;

export {getVariable, queryVariables, variableSchema, queryVariablesRequestBodySchema, queryVariablesResponseBodySchema};

export type {Variable, QueryVariablesRequestBody, QueryVariablesResponseBody};
