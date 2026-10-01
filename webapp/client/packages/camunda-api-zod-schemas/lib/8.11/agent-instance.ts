/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {API_VERSION, type Endpoint} from './common';
import {agentInstanceDefinitionResultSchema} from './gen/zod/agentInstanceDefinitionResultSchema';
import {agentInstanceDocumentContentSchema} from './gen/zod/agentInstanceDocumentContentSchema';
import {agentInstanceFilterSchema} from './gen/zod/agentInstanceFilterSchema';
import {agentInstanceHistoryCommitStatusEnumSchema} from './gen/zod/agentInstanceHistoryCommitStatusEnumSchema';
import {agentInstanceHistoryFilterSchema} from './gen/zod/agentInstanceHistoryFilterSchema';
import {agentInstanceHistoryItemMetricsSchema} from './gen/zod/agentInstanceHistoryItemMetricsSchema';
import {agentInstanceHistoryItemResultSchema} from './gen/zod/agentInstanceHistoryItemResultSchema';
import {agentInstanceHistoryRoleEnumSchema} from './gen/zod/agentInstanceHistoryRoleEnumSchema';
import {agentInstanceHistorySearchQueryResultSchema} from './gen/zod/agentInstanceHistorySearchQueryResultSchema';
import {agentInstanceHistorySearchQuerySchema} from './gen/zod/agentInstanceHistorySearchQuerySchema';
import {agentInstanceLimitsSchema} from './gen/zod/agentInstanceLimitsSchema';
import {agentInstanceMessageContentSchema} from './gen/zod/agentInstanceMessageContentSchema';
import {agentInstanceMetricsSchema} from './gen/zod/agentInstanceMetricsSchema';
import {agentInstanceObjectContentSchema} from './gen/zod/agentInstanceObjectContentSchema';
import {agentInstanceResultSchema} from './gen/zod/agentInstanceResultSchema';
import {agentInstanceSearchQueryResultSchema} from './gen/zod/agentInstanceSearchQueryResultSchema';
import {agentInstanceSearchQuerySchema} from './gen/zod/agentInstanceSearchQuerySchema';
import {agentInstanceStatusEnumSchema} from './gen/zod/agentInstanceStatusEnumSchema';
import {agentInstanceTextContentSchema} from './gen/zod/agentInstanceTextContentSchema';
import {agentInstanceToolCallSchema} from './gen/zod/agentInstanceToolCallSchema';
import {agentToolSchema} from './gen/zod/agentToolSchema';
import {getAgentInstanceStatus200Schema} from './gen/zod/getAgentInstanceSchema';
import type {AgentInstanceDefinitionResult} from './gen/types/AgentInstanceDefinitionResult';
import type {AgentInstanceDocumentContent} from './gen/types/AgentInstanceDocumentContent';
import type {AgentInstanceFilter} from './gen/types/AgentInstanceFilter';
import type {AgentInstanceHistoryCommitStatusEnumKey} from './gen/types/AgentInstanceHistoryCommitStatusEnum';
import type {AgentInstanceHistoryFilter} from './gen/types/AgentInstanceHistoryFilter';
import type {AgentInstanceHistoryItemMetrics} from './gen/types/AgentInstanceHistoryItemMetrics';
import type {AgentInstanceHistoryItemResult} from './gen/types/AgentInstanceHistoryItemResult';
import type {AgentInstanceHistoryRoleEnumKey} from './gen/types/AgentInstanceHistoryRoleEnum';
import type {AgentInstanceHistorySearchQuery} from './gen/types/AgentInstanceHistorySearchQuery';
import type {AgentInstanceHistorySearchQueryResult} from './gen/types/AgentInstanceHistorySearchQueryResult';
import type {AgentInstanceLimits} from './gen/types/AgentInstanceLimits';
import type {AgentInstanceMessageContent} from './gen/types/AgentInstanceMessageContent';
import type {AgentInstanceMetrics} from './gen/types/AgentInstanceMetrics';
import type {AgentInstanceObjectContent} from './gen/types/AgentInstanceObjectContent';
import type {AgentInstanceResult} from './gen/types/AgentInstanceResult';
import type {AgentInstanceSearchQuery} from './gen/types/AgentInstanceSearchQuery';
import type {AgentInstanceSearchQueryResult} from './gen/types/AgentInstanceSearchQueryResult';
import type {AgentInstanceStatusEnumKey} from './gen/types/AgentInstanceStatusEnum';
import type {AgentInstanceTextContent} from './gen/types/AgentInstanceTextContent';
import type {AgentInstanceToolCall} from './gen/types/AgentInstanceToolCall';
import type {AgentTool} from './gen/types/AgentTool';
import type {GetAgentInstanceStatus200} from './gen/types/GetAgentInstance';

const agentInstanceStatusSchema = agentInstanceStatusEnumSchema;
type AgentInstanceStatus = AgentInstanceStatusEnumKey;

const agentInstanceDefinitionSchema = agentInstanceDefinitionResultSchema;
type AgentInstanceDefinition = AgentInstanceDefinitionResult;

const agentInstanceSchema = agentInstanceResultSchema;
type AgentInstance = AgentInstanceResult;

const queryAgentInstancesRequestBodySchema = agentInstanceSearchQuerySchema;
type QueryAgentInstancesRequestBody = AgentInstanceSearchQuery;

const queryAgentInstancesResponseBodySchema = agentInstanceSearchQueryResultSchema;
type QueryAgentInstancesResponseBody = AgentInstanceSearchQueryResult;

const getAgentInstanceResponseBodySchema = getAgentInstanceStatus200Schema;
type GetAgentInstanceResponseBody = GetAgentInstanceStatus200;

const agentInstanceHistoryRoleSchema = agentInstanceHistoryRoleEnumSchema;
type AgentInstanceHistoryRole = AgentInstanceHistoryRoleEnumKey;

const agentInstanceHistoryCommitStatusSchema = agentInstanceHistoryCommitStatusEnumSchema;
type AgentInstanceHistoryCommitStatus = AgentInstanceHistoryCommitStatusEnumKey;

// Gen `agentInstanceHistoryItemSchema` is the update-request item. The search result item is `agentInstanceHistoryItemResultSchema`.
const agentInstanceHistoryItemSchema = agentInstanceHistoryItemResultSchema;
type AgentInstanceHistoryItem = AgentInstanceHistoryItemResult;

const queryAgentInstanceHistoryRequestBodySchema = agentInstanceHistorySearchQuerySchema;
type QueryAgentInstanceHistoryRequestBody = AgentInstanceHistorySearchQuery;

const queryAgentInstanceHistoryResponseBodySchema = agentInstanceHistorySearchQueryResultSchema;
type QueryAgentInstanceHistoryResponseBody = AgentInstanceHistorySearchQueryResult;

const getAgentInstance = {
	method: 'GET',
	getUrl: ({agentInstanceKey}) => `/${API_VERSION}/agent-instances/${agentInstanceKey}` as const,
} as const satisfies Endpoint<{agentInstanceKey: string}>;

const queryAgentInstances = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/agent-instances/search` as const,
} as const satisfies Endpoint;

const queryAgentInstanceHistory = {
	method: 'POST',
	getUrl: ({agentInstanceKey}) => `/${API_VERSION}/agent-instances/${agentInstanceKey}/history/search` as const,
} as const satisfies Endpoint<{agentInstanceKey: string}>;

export {
	agentInstanceStatusSchema,
	agentInstanceDefinitionSchema,
	agentInstanceMetricsSchema,
	agentInstanceLimitsSchema,
	agentToolSchema,
	agentInstanceSchema,
	agentInstanceFilterSchema,
	queryAgentInstancesRequestBodySchema,
	queryAgentInstancesResponseBodySchema,
	getAgentInstanceResponseBodySchema,
	agentInstanceHistoryRoleSchema,
	agentInstanceHistoryCommitStatusSchema,
	agentInstanceTextContentSchema,
	agentInstanceDocumentContentSchema,
	agentInstanceObjectContentSchema,
	agentInstanceMessageContentSchema,
	agentInstanceToolCallSchema,
	agentInstanceHistoryItemMetricsSchema,
	agentInstanceHistoryItemSchema,
	agentInstanceHistoryFilterSchema,
	queryAgentInstanceHistoryRequestBodySchema,
	queryAgentInstanceHistoryResponseBodySchema,
	getAgentInstance,
	queryAgentInstances,
	queryAgentInstanceHistory,
};
export type {
	AgentInstanceStatus,
	AgentInstanceDefinition,
	AgentInstanceMetrics,
	AgentInstanceLimits,
	AgentTool,
	AgentInstance,
	AgentInstanceFilter,
	QueryAgentInstancesRequestBody,
	QueryAgentInstancesResponseBody,
	GetAgentInstanceResponseBody,
	AgentInstanceHistoryRole,
	AgentInstanceHistoryCommitStatus,
	AgentInstanceTextContent,
	AgentInstanceDocumentContent,
	AgentInstanceObjectContent,
	AgentInstanceMessageContent,
	AgentInstanceToolCall,
	AgentInstanceHistoryItemMetrics,
	AgentInstanceHistoryItem,
	AgentInstanceHistoryFilter,
	QueryAgentInstanceHistoryRequestBody,
	QueryAgentInstanceHistoryResponseBody,
};
