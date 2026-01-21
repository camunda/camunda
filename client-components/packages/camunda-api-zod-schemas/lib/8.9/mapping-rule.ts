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

const createMappingRule: Endpoint = {
	method: 'POST',
	getUrl() {
		return `/${API_VERSION}/mapping-rules`;
	},
};

const updateMappingRule: Endpoint<{mappingRuleId: string}> = {
	method: 'PUT',
	getUrl(params) {
		const {mappingRuleId} = params;

		return `/${API_VERSION}/mapping-rules/${mappingRuleId}`;
	},
};

const deleteMappingRule: Endpoint<{mappingRuleId: string}> = {
	method: 'DELETE',
	getUrl(params) {
		const {mappingRuleId} = params;

		return `/${API_VERSION}/mapping-rules/${mappingRuleId}`;
	},
};

const getMappingRule: Endpoint<{mappingRuleId: string}> = {
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
