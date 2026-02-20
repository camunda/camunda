/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {API_VERSION, getQueryRequestBodySchema, getQueryResponseBodySchema, type Endpoint} from './common';

const mappingRuleSchema = z.object({
	mappingId: z.string(),
	claimName: z.string(),
	claimValue: z.string(),
	name: z.string(),
});
type MappingRule = z.infer<typeof mappingRuleSchema>;

const mappingRuleResultSchema = mappingRuleSchema.omit({mappingId: true}).extend({
	mappingRuleId: z.string(),
});
type MappingRuleResult = z.infer<typeof mappingRuleResultSchema>;

const createMappingRuleRequestBodySchema = mappingRuleSchema;
type CreateMappingRuleRequestBody = z.infer<typeof createMappingRuleRequestBodySchema>;

const createMappingRuleResponseBodySchema = mappingRuleResultSchema;
type CreateMappingRuleResponseBody = z.infer<typeof createMappingRuleResponseBodySchema>;

const updateMappingRuleRequestBodySchema = mappingRuleSchema.pick({
	claimName: true,
	claimValue: true,
	name: true,
});
type UpdateMappingRuleRequestBody = z.infer<typeof updateMappingRuleRequestBodySchema>;

const updateMappingRuleResponseBodySchema = mappingRuleResultSchema;
type UpdateMappingRuleResponseBody = z.infer<typeof updateMappingRuleResponseBodySchema>;

const queryMappingRulesRequestBodySchema = getQueryRequestBodySchema({
	sortFields: ['mappingId', 'claimName', 'claimValue', 'name'] as const,
	filter: mappingRuleSchema
		.pick({
			mappingId: true,
			claimName: true,
			claimValue: true,
			name: true,
		})
		.partial(),
});
type QueryMappingRulesRequestBody = z.infer<typeof queryMappingRulesRequestBodySchema>;

const queryMappingRulesResponseBodySchema = getQueryResponseBodySchema(mappingRuleResultSchema);
type QueryMappingRulesResponseBody = z.infer<typeof queryMappingRulesResponseBodySchema>;

const getMappingRuleResponseBodySchema = mappingRuleResultSchema;
type GetMappingRuleResponseBody = z.infer<typeof getMappingRuleResponseBodySchema>;

const createMappingRule: Endpoint = {
	method: 'POST',
	getUrl() {
		return `/${API_VERSION}/mapping-rules`;
	},
};

const updateMappingRule: Endpoint<Pick<MappingRuleResult, 'mappingRuleId'>> = {
	method: 'PUT',
	getUrl(params) {
		const {mappingRuleId} = params;

		return `/${API_VERSION}/mapping-rules/${mappingRuleId}`;
	},
};

const deleteMappingRule: Endpoint<Pick<MappingRuleResult, 'mappingRuleId'>> = {
	method: 'DELETE',
	getUrl(params) {
		const {mappingRuleId} = params;

		return `/${API_VERSION}/mapping-rules/${mappingRuleId}`;
	},
};

const getMappingRule: Endpoint<Pick<MappingRuleResult, 'mappingRuleId'>> = {
	method: 'GET',
	getUrl(params) {
		const {mappingRuleId} = params;

		return `/${API_VERSION}/mapping-rules/${mappingRuleId}`;
	},
};

const queryMappingRules: Endpoint = {
	method: 'POST',
	getUrl() {
		return `/${API_VERSION}/mapping-rules/search`;
	},
};

export {
	createMappingRule,
	updateMappingRule,
	deleteMappingRule,
	getMappingRule,
	queryMappingRules,
	createMappingRuleRequestBodySchema,
	createMappingRuleResponseBodySchema,
	updateMappingRuleRequestBodySchema,
	updateMappingRuleResponseBodySchema,
	getMappingRuleResponseBodySchema,
	queryMappingRulesRequestBodySchema,
	queryMappingRulesResponseBodySchema,
	mappingRuleSchema,
	mappingRuleResultSchema,
};
export type {
	CreateMappingRuleRequestBody,
	CreateMappingRuleResponseBody,
	UpdateMappingRuleRequestBody,
	UpdateMappingRuleResponseBody,
	GetMappingRuleResponseBody,
	QueryMappingRulesRequestBody,
	QueryMappingRulesResponseBody,
	MappingRule,
	MappingRuleResult,
};
