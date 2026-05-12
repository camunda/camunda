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
	basicStringFilterSchema,
	getEnumFilterSchema,
	getQueryRequestBodySchema,
	getQueryResponseBodySchema,
	type Endpoint,
} from './common';

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

const getAgentInstance: Endpoint<{agentInstanceKey: string}> = {
	method: 'GET',
	getUrl: ({agentInstanceKey}) => `/${API_VERSION}/agent-instances/${agentInstanceKey}`,
};

const searchAgentInstances: Endpoint = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/agent-instances/search`,
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
	getAgentInstance,
	searchAgentInstances,
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
};
