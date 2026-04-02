/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {
	advancedStringFilterSchema,
	API_VERSION,
	getQueryRequestBodySchema,
	getQueryResponseBodySchema,
	type Endpoint,
} from './common';

const variableSchema = z.object({
	name: z.string(),
	value: z.string(),
	tenantId: z.string(),
	isTruncated: z.boolean().nullable(),
	variableKey: z.string(),
	scopeKey: z.string(),
	processInstanceKey: z.string(),
	rootProcessInstanceKey: z.string().nullable(),
});

type Variable = z.infer<typeof variableSchema>;

const getVariable: Endpoint<Pick<Variable, 'variableKey'>> = {
	method: 'GET',
	getUrl: ({variableKey}) => `/${API_VERSION}/variables/${variableKey}`,
};

const queryVariablesRequestBodySchema = getQueryRequestBodySchema({
	sortFields: ['name', 'value', 'fullValue', 'tenantId', 'variableKey', 'scopeKey', 'processInstanceKey'] as const,
	filter: z
		.object({
			name: advancedStringFilterSchema,
			value: advancedStringFilterSchema,
			variableKey: advancedStringFilterSchema,
			scopeKey: advancedStringFilterSchema,
			processInstanceKey: advancedStringFilterSchema,
			...variableSchema.pick({
				tenantId: true,
				isTruncated: true,
			}).shape,
		})
		.partial(),
});
type QueryVariablesRequestBody = z.infer<typeof queryVariablesRequestBodySchema>;

const queryVariablesResponseBodySchema = getQueryResponseBodySchema(variableSchema);
type QueryVariablesResponseBody = z.infer<typeof queryVariablesResponseBodySchema>;

const queryVariables: Endpoint<{truncateValues?: boolean}> = {
	method: 'POST',
	getUrl: ({truncateValues} = {}) =>
		`/${API_VERSION}/variables/search${truncateValues !== undefined ? `?truncateValues=${truncateValues}` : ''}`,
};

export {getVariable, queryVariables, variableSchema, queryVariablesRequestBodySchema, queryVariablesResponseBodySchema};
export type {Variable, QueryVariablesRequestBody, QueryVariablesResponseBody};
