/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {API_VERSION, type Endpoint} from '../common';
import {
	mappingRuleResultSchema,
	mappingRuleCreateRequestSchema,
	mappingRuleUpdateRequestSchema,
	mappingRuleSearchQueryRequestSchema,
	mappingRuleSearchQueryResultSchema,
} from './gen';

const mappingRuleSchema = mappingRuleResultSchema;
type MappingRule = z.infer<typeof mappingRuleSchema>;

const createMappingRuleRequestBodySchema = mappingRuleCreateRequestSchema;
type CreateMappingRuleRequestBody = z.infer<typeof createMappingRuleRequestBodySchema>;

const createMappingRuleResponseBodySchema = mappingRuleResultSchema;
type CreateMappingRuleResponseBody = z.infer<typeof createMappingRuleResponseBodySchema>;

const updateMappingRuleRequestBodySchema = mappingRuleUpdateRequestSchema;
type UpdateMappingRuleRequestBody = z.infer<typeof updateMappingRuleRequestBodySchema>;

const updateMappingRuleResponseBodySchema = mappingRuleResultSchema;
type UpdateMappingRuleResponseBody = z.infer<typeof updateMappingRuleResponseBodySchema>;

const queryMappingRulesRequestBodySchema = mappingRuleSearchQueryRequestSchema;
type QueryMappingRulesRequestBody = z.infer<typeof queryMappingRulesRequestBodySchema>;

const queryMappingRulesResponseBodySchema = mappingRuleSearchQueryResultSchema;
type QueryMappingRulesResponseBody = z.infer<typeof queryMappingRulesResponseBodySchema>;

const createMappingRule = {
	method: 'POST',
	getUrl() {
		return `/${API_VERSION}/mapping-rules` as const;
	},
} as const satisfies Endpoint;

const updateMappingRule = {
	method: 'PUT',
	getUrl(params) {
		const {mappingRuleId} = params;

		return `/${API_VERSION}/mapping-rules/${mappingRuleId}` as const;
	},
} as const satisfies Endpoint<Pick<MappingRule, 'mappingRuleId'>>;

const deleteMappingRule = {
	method: 'DELETE',
	getUrl(params) {
		const {mappingRuleId} = params;

		return `/${API_VERSION}/mapping-rules/${mappingRuleId}` as const;
	},
} as const satisfies Endpoint<Pick<MappingRule, 'mappingRuleId'>>;

const getMappingRule = {
	method: 'GET',
	getUrl(params) {
		const {mappingRuleId} = params;

		return `/${API_VERSION}/mapping-rules/${mappingRuleId}` as const;
	},
} as const satisfies Endpoint<Pick<MappingRule, 'mappingRuleId'>>;

const queryMappingRules = {
	method: 'POST',
	getUrl() {
		return `/${API_VERSION}/mapping-rules/search` as const;
	},
} as const satisfies Endpoint;

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
