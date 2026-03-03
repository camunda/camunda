/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {API_VERSION, getQueryRequestBodySchema, getQueryResponseBodySchema, type Endpoint} from '../common';

const mappingRuleSchema = z.object({
	mappingRuleId: z.string(),
	claimName: z.string(),
	claimValue: z.string(),
	name: z.string(),
});
type MappingRule = z.infer<typeof mappingRuleSchema>;

const createMappingRuleRequestBodySchema = mappingRuleSchema;
type CreateMappingRuleRequestBody = z.infer<typeof createMappingRuleRequestBodySchema>;

const createMappingRuleResponseBodySchema = mappingRuleSchema;
type CreateMappingRuleResponseBody = z.infer<typeof createMappingRuleResponseBodySchema>;

const updateMappingRuleRequestBodySchema = mappingRuleSchema.pick({
	claimName: true,
	claimValue: true,
	name: true,
});
type UpdateMappingRuleRequestBody = z.infer<typeof updateMappingRuleRequestBodySchema>;

const updateMappingRuleResponseBodySchema = mappingRuleSchema;
type UpdateMappingRuleResponseBody = z.infer<typeof updateMappingRuleResponseBodySchema>;

const queryMappingRulesRequestBodySchema = getQueryRequestBodySchema({
	sortFields: ['mappingRuleId', 'claimName', 'claimValue', 'name'] as const,
	filter: mappingRuleSchema
		.pick({
			mappingRuleId: true,
			claimName: true,
			claimValue: true,
			name: true,
		})
		.partial(),
});
type QueryMappingRulesRequestBody = z.infer<typeof queryMappingRulesRequestBodySchema>;

const queryMappingRulesResponseBodySchema = getQueryResponseBodySchema(mappingRuleSchema);
type QueryMappingRulesResponseBody = z.infer<typeof queryMappingRulesResponseBodySchema>;

const createMappingRule: Endpoint = {
	method: 'POST',
	getUrl() {
		return `/${API_VERSION}/mapping-rules`;
	},
};

const updateMappingRule: Endpoint<Pick<MappingRule, 'mappingRuleId'>> = {
	method: 'PUT',
	getUrl(params) {
		const {mappingRuleId} = params;

		return `/${API_VERSION}/mapping-rules/${mappingRuleId}`;
	},
};

const deleteMappingRule: Endpoint<Pick<MappingRule, 'mappingRuleId'>> = {
	method: 'DELETE',
	getUrl(params) {
		const {mappingRuleId} = params;

		return `/${API_VERSION}/mapping-rules/${mappingRuleId}`;
	},
};

const getMappingRule: Endpoint<Pick<MappingRule, 'mappingRuleId'>> = {
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
	queryMappingRulesRequestBodySchema,
	queryMappingRulesResponseBodySchema,
	mappingRuleSchema,
};
export type {
	CreateMappingRuleRequestBody,
	CreateMappingRuleResponseBody,
	UpdateMappingRuleRequestBody,
	UpdateMappingRuleResponseBody,
	QueryMappingRulesRequestBody,
	QueryMappingRulesResponseBody,
	MappingRule,
};
