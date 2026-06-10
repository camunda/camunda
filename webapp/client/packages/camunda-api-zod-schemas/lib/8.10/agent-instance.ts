/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {
	API_VERSION,
	advancedDateTimeFilterSchema,
	advancedIntegerFilterSchema,
	basicStringFilterSchema,
	getEnumFilterSchema,
	getQueryRequestBodySchema,
	getQueryResponseBodySchema,
	type Endpoint,
} from './common';
import {documentReferenceSchema} from './document';

const agentInstanceStatusSchema = z.enum([
	'COMPLETED',
	'IDLE',
	'INITIALIZING',
	'THINKING',
	'TOOL_CALLING',
	'TOOL_DISCOVERY',
]);
type AgentInstanceStatus = z.infer<typeof agentInstanceStatusSchema>;

const agentInstanceDefinitionSchema = z.object({
	model: z.string(),
	provider: z.string(),
	systemPrompt: z.string(),
});
type AgentInstanceDefinition = z.infer<typeof agentInstanceDefinitionSchema>;

const agentInstanceMetricsSchema = z.object({
	inputTokens: z.number(),
	outputTokens: z.number(),
	modelCalls: z.number(),
	toolCalls: z.number(),
});
type AgentInstanceMetrics = z.infer<typeof agentInstanceMetricsSchema>;

const agentInstanceLimitsSchema = z.object({
	maxModelCalls: z.number(),
	maxToolCalls: z.number(),
	maxTokens: z.number(),
});
type AgentInstanceLimits = z.infer<typeof agentInstanceLimitsSchema>;

const agentInstanceSchema = z.object({
	agentInstanceKey: z.string(),
	status: agentInstanceStatusSchema,
	definition: agentInstanceDefinitionSchema,
	metrics: agentInstanceMetricsSchema,
	limits: agentInstanceLimitsSchema,
	elementId: z.string(),
	processInstanceKey: z.string(),
	processDefinitionKey: z.string(),
	tenantId: z.string(),
	creationDate: z.string(),
	lastUpdatedDate: z.string(),
	completionDate: z.string().nullable(),
	elementInstanceKeys: z.array(z.string()),
});
type AgentInstance = z.infer<typeof agentInstanceSchema>;

const agentInstanceFilterSchema = z
	.object({
		agentInstanceKey: basicStringFilterSchema,
		status: getEnumFilterSchema(agentInstanceStatusSchema),
		elementId: basicStringFilterSchema,
		processInstanceKey: basicStringFilterSchema,
		processDefinitionKey: basicStringFilterSchema,
		tenantId: basicStringFilterSchema,
		creationDate: advancedDateTimeFilterSchema,
		lastUpdatedDate: advancedDateTimeFilterSchema,
		completionDate: advancedDateTimeFilterSchema,
		elementInstanceKeys: z.array(basicStringFilterSchema),
	})
	.partial();
type AgentInstanceFilter = z.infer<typeof agentInstanceFilterSchema>;

const queryAgentInstancesRequestBodySchema = getQueryRequestBodySchema({
	sortFields: ['creationDate', 'lastUpdatedDate', 'completionDate', 'status'] as const,
	filter: agentInstanceFilterSchema,
});
type QueryAgentInstancesRequestBody = z.infer<typeof queryAgentInstancesRequestBodySchema>;

const queryAgentInstancesResponseBodySchema = getQueryResponseBodySchema(agentInstanceSchema);
type QueryAgentInstancesResponseBody = z.infer<typeof queryAgentInstancesResponseBodySchema>;

const getAgentInstanceResponseBodySchema = agentInstanceSchema;
type GetAgentInstanceResponseBody = z.infer<typeof getAgentInstanceResponseBodySchema>;

const agentInstanceHistoryRoleSchema = z.enum(['USER', 'ASSISTANT', 'TOOL_RESULT']);
type AgentInstanceHistoryRole = z.infer<typeof agentInstanceHistoryRoleSchema>;

const agentInstanceHistoryCommitStatusSchema = z.enum(['COMMITTED', 'PENDING', 'DISCARDED']);
type AgentInstanceHistoryCommitStatus = z.infer<typeof agentInstanceHistoryCommitStatusSchema>;

const agentInstanceTextContentSchema = z.object({
	contentType: z.literal('TEXT'),
	text: z.string(),
});
type AgentInstanceTextContent = z.infer<typeof agentInstanceTextContentSchema>;

const agentInstanceDocumentContentSchema = z.object({
	contentType: z.literal('DOCUMENT'),
	documentReference: documentReferenceSchema,
});
type AgentInstanceDocumentContent = z.infer<typeof agentInstanceDocumentContentSchema>;

const agentInstanceObjectContentSchema = z.object({
	contentType: z.literal('OBJECT'),
	object: z.record(z.string(), z.unknown()),
});
type AgentInstanceObjectContent = z.infer<typeof agentInstanceObjectContentSchema>;

const agentInstanceMessageContentSchema = z.discriminatedUnion('contentType', [
	agentInstanceTextContentSchema,
	agentInstanceDocumentContentSchema,
	agentInstanceObjectContentSchema,
]);
type AgentInstanceMessageContent = z.infer<typeof agentInstanceMessageContentSchema>;

const agentInstanceToolCallSchema = z.object({
	toolCallId: z.string(),
	toolName: z.string(),
	elementId: z.string().nullable(),
	arguments: z.record(z.string(), z.unknown()).nullable(),
});
type AgentInstanceToolCall = z.infer<typeof agentInstanceToolCallSchema>;

const agentInstanceHistoryItemMetricsSchema = z.object({
	inputTokens: z.number(),
	outputTokens: z.number(),
	durationMs: z.number(),
});
type AgentInstanceHistoryItemMetrics = z.infer<typeof agentInstanceHistoryItemMetricsSchema>;

const agentInstanceHistoryItemSchema = z.object({
	historyItemKey: z.string(),
	agentInstanceKey: z.string(),
	elementInstanceKey: z.string(),
	jobKey: z.string(),
	jobLease: z.string(),
	iteration: z.number().int().nullable(),
	role: agentInstanceHistoryRoleSchema,
	content: z.array(agentInstanceMessageContentSchema),
	toolCalls: z.array(agentInstanceToolCallSchema),
	metrics: agentInstanceHistoryItemMetricsSchema.nullable(),
	commitStatus: agentInstanceHistoryCommitStatusSchema,
	producedAt: z.string(),
});
type AgentInstanceHistoryItem = z.infer<typeof agentInstanceHistoryItemSchema>;

const agentInstanceHistoryFilterSchema = z
	.object({
		historyItemKey: basicStringFilterSchema,
		role: getEnumFilterSchema(agentInstanceHistoryRoleSchema),
		elementInstanceKey: basicStringFilterSchema,
		jobKey: basicStringFilterSchema,
		iteration: advancedIntegerFilterSchema,
		commitStatus: getEnumFilterSchema(agentInstanceHistoryCommitStatusSchema),
		producedAt: advancedDateTimeFilterSchema,
	})
	.partial();
type AgentInstanceHistoryFilter = z.infer<typeof agentInstanceHistoryFilterSchema>;

const createAgentInstanceHistoryItemRequestBodySchema = z.object({
	elementInstanceKey: z.string(),
	jobKey: z.string(),
	jobLease: z.string(),
	iteration: z.number().int().nullable().optional(),
	role: agentInstanceHistoryRoleSchema,
	content: z.array(agentInstanceMessageContentSchema),
	toolCalls: z.array(agentInstanceToolCallSchema).nullable().optional(),
	metrics: agentInstanceHistoryItemMetricsSchema.nullable().optional(),
	producedAt: z.string(),
});
type CreateAgentInstanceHistoryItemRequestBody = z.infer<typeof createAgentInstanceHistoryItemRequestBodySchema>;

const createAgentInstanceHistoryItemResponseBodySchema = z.object({
	historyItemKey: z.string(),
});
type CreateAgentInstanceHistoryItemResponseBody = z.infer<typeof createAgentInstanceHistoryItemResponseBodySchema>;

const searchAgentInstanceHistoryRequestBodySchema = getQueryRequestBodySchema({
	sortFields: ['producedAt', 'historyItemKey', 'iteration'] as const,
	filter: agentInstanceHistoryFilterSchema,
});
type SearchAgentInstanceHistoryRequestBody = z.infer<typeof searchAgentInstanceHistoryRequestBodySchema>;

const searchAgentInstanceHistoryResponseBodySchema = getQueryResponseBodySchema(agentInstanceHistoryItemSchema);
type SearchAgentInstanceHistoryResponseBody = z.infer<typeof searchAgentInstanceHistoryResponseBodySchema>;

const getAgentInstance: Endpoint<{agentInstanceKey: string}> = {
	method: 'GET',
	getUrl: ({agentInstanceKey}) => `/${API_VERSION}/agent-instances/${agentInstanceKey}`,
};

const searchAgentInstances: Endpoint = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/agent-instances/search`,
};

const createAgentInstanceHistoryItem: Endpoint<{agentInstanceKey: string}> = {
	method: 'POST',
	getUrl: ({agentInstanceKey}) => `/${API_VERSION}/agent-instances/${agentInstanceKey}/history`,
};

const searchAgentInstanceHistory: Endpoint<{agentInstanceKey: string}> = {
	method: 'POST',
	getUrl: ({agentInstanceKey}) => `/${API_VERSION}/agent-instances/${agentInstanceKey}/history/search`,
};

export {
	agentInstanceStatusSchema,
	agentInstanceDefinitionSchema,
	agentInstanceMetricsSchema,
	agentInstanceLimitsSchema,
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
	createAgentInstanceHistoryItemRequestBodySchema,
	createAgentInstanceHistoryItemResponseBodySchema,
	searchAgentInstanceHistoryRequestBodySchema,
	searchAgentInstanceHistoryResponseBodySchema,
	getAgentInstance,
	searchAgentInstances,
	createAgentInstanceHistoryItem,
	searchAgentInstanceHistory,
};
export type {
	AgentInstanceStatus,
	AgentInstanceDefinition,
	AgentInstanceMetrics,
	AgentInstanceLimits,
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
	CreateAgentInstanceHistoryItemRequestBody,
	CreateAgentInstanceHistoryItemResponseBody,
	SearchAgentInstanceHistoryRequestBody,
	SearchAgentInstanceHistoryResponseBody,
};
