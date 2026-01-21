/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {API_VERSION, type Endpoint} from '../common';
import {variableSearchResultSchema, variableSearchQuerySchema, variableSearchQueryResultSchema} from './gen';

const variableSchema = variableSearchResultSchema;
type Variable = z.infer<typeof variableSchema>;

const getVariable = {
	method: 'GET',
	getUrl: ({variableKey}) => `/${API_VERSION}/variables/${variableKey}` as const,
} as const satisfies Endpoint<Pick<Variable, 'variableKey'>>;

const queryVariablesRequestBodySchema = variableSearchQuerySchema;
type QueryVariablesRequestBody = z.infer<typeof queryVariablesRequestBodySchema>;

const queryVariablesResponseBodySchema = variableSearchQueryResultSchema;
type QueryVariablesResponseBody = z.infer<typeof queryVariablesResponseBodySchema>;

const queryVariables = {
	method: 'POST',
	getUrl: ({truncateValues} = {}) =>
		`/${API_VERSION}/variables/search${truncateValues !== undefined ? `?truncateValues=${truncateValues}` : ''}` as const,
} as const satisfies Endpoint<{truncateValues?: boolean}>;

export {getVariable, queryVariables, variableSchema, queryVariablesRequestBodySchema, queryVariablesResponseBodySchema};
export type {Variable, QueryVariablesRequestBody, QueryVariablesResponseBody};
