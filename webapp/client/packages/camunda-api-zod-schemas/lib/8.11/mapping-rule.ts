/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {API_VERSION, type Endpoint} from './common';
import {getMappingRuleStatus200Schema} from './gen/zod/getMappingRuleSchema';
import {mappingRuleCreateRequestSchema} from './gen/zod/mappingRuleCreateRequestSchema';
import {mappingRuleCreateResultSchema} from './gen/zod/mappingRuleCreateResultSchema';
import {mappingRuleResultSchema} from './gen/zod/mappingRuleResultSchema';
import {mappingRuleSearchQueryRequestSchema} from './gen/zod/mappingRuleSearchQueryRequestSchema';
import {mappingRuleSearchQueryResultSchema} from './gen/zod/mappingRuleSearchQueryResultSchema';
import {mappingRuleUpdateRequestSchema} from './gen/zod/mappingRuleUpdateRequestSchema';
import {mappingRuleUpdateResultSchema} from './gen/zod/mappingRuleUpdateResultSchema';
import type {GetMappingRuleStatus200} from './gen/types/GetMappingRule';
import type {MappingRuleCreateRequest} from './gen/types/MappingRuleCreateRequest';
import type {MappingRuleCreateResult} from './gen/types/MappingRuleCreateResult';
import type {MappingRuleResult} from './gen/types/MappingRuleResult';
import type {MappingRuleSearchQueryRequest} from './gen/types/MappingRuleSearchQueryRequest';
import type {MappingRuleSearchQueryResult} from './gen/types/MappingRuleSearchQueryResult';
import type {MappingRuleUpdateRequest} from './gen/types/MappingRuleUpdateRequest';
import type {MappingRuleUpdateResult} from './gen/types/MappingRuleUpdateResult';

const mappingRuleSchema = mappingRuleResultSchema;
type MappingRule = MappingRuleResult;

const createMappingRuleRequestBodySchema = mappingRuleCreateRequestSchema;
type CreateMappingRuleRequestBody = MappingRuleCreateRequest;

const createMappingRuleResponseBodySchema = mappingRuleCreateResultSchema;
type CreateMappingRuleResponseBody = MappingRuleCreateResult;

const updateMappingRuleRequestBodySchema = mappingRuleUpdateRequestSchema;
type UpdateMappingRuleRequestBody = MappingRuleUpdateRequest;

const updateMappingRuleResponseBodySchema = mappingRuleUpdateResultSchema;
type UpdateMappingRuleResponseBody = MappingRuleUpdateResult;

const queryMappingRulesRequestBodySchema = mappingRuleSearchQueryRequestSchema;
type QueryMappingRulesRequestBody = MappingRuleSearchQueryRequest;

const queryMappingRulesResponseBodySchema = mappingRuleSearchQueryResultSchema;
type QueryMappingRulesResponseBody = MappingRuleSearchQueryResult;

const getMappingRuleResponseBodySchema = getMappingRuleStatus200Schema;
type GetMappingRuleResponseBody = GetMappingRuleStatus200;

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
	getMappingRuleResponseBodySchema,
	queryMappingRulesRequestBodySchema,
	queryMappingRulesResponseBodySchema,
	mappingRuleSchema,
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
};
