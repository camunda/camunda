/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
	AgentInstance,
	AgentInstanceHistoryItem,
	QueryAgentInstanceHistoryResponseBody,
	QueryAgentInstancesResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.11';
import {createPaginatedResponse} from './shared';

function createAgentInstance(overrides?: Partial<AgentInstance>): AgentInstance {
	return {
		agentInstanceKey: 'agent-1',
		agentDefinitionKey: 'definition-1',
		status: 'THINKING',
		definition: {model: 'model', provider: 'provider', systemPrompt: []},
		metrics: {
			inputTokens: 0,
			outputTokens: 0,
			reasoningTokenCount: 0,
			cacheCreationTokenCount: 0,
			cacheReadTokenCount: 0,
			modelCalls: 0,
			toolCalls: 0,
		},
		limits: {maxModelCalls: 0, maxToolCalls: 0, maxTokens: 0},
		tools: [],
		elementId: 'task_1',
		processInstanceKey: 'instance-1',
		rootProcessInstanceKey: 'instance-1',
		processDefinitionKey: 'definition-1',
		processDefinitionId: 'Process_1',
		processDefinitionVersion: 1,
		processDefinitionVersionTag: null,
		tenantId: '<default>',
		creationDate: '2026-01-15T10:00:00.000Z',
		lastUpdatedDate: '2026-01-15T10:00:00.000Z',
		completionDate: null,
		elementInstanceKeys: [],
		...overrides,
	};
}

function createQueryAgentInstancesResponse(
	items: AgentInstance[] = [],
	{totalItems = items.length, hasMoreTotalItems = false}: {totalItems?: number; hasMoreTotalItems?: boolean} = {},
): QueryAgentInstancesResponseBody {
	return createPaginatedResponse({
		items,
		page: {totalItems, startCursor: null, endCursor: null, hasMoreTotalItems},
	});
}

function createAgentInstanceHistoryItem(overrides?: Partial<AgentInstanceHistoryItem>): AgentInstanceHistoryItem {
	return {
		historyItemKey: 'history-1',
		historyItemId: 'history-id-1',
		agentInstanceKey: 'agent-1',
		elementInstanceKey: 'element-instance-1',
		jobKey: 'job-1',
		jobLeaseToken: 'lease-1',
		loopIteration: 1,
		role: 'ASSISTANT',
		content: [],
		toolCalls: [],
		metrics: null,
		commitStatus: 'COMMITTED',
		producedAt: '2026-01-15T10:00:00.000Z',
		tools: [],
		model: null,
		provider: null,
		limits: {maxModelCalls: 0, maxToolCalls: 0, maxTokens: 0},
		systemPrompt: [],
		...overrides,
	};
}

function createQueryAgentInstanceHistoryResponse(
	items: AgentInstanceHistoryItem[] = [],
	{
		totalItems = items.length,
		endCursor = null,
		hasMoreTotalItems = false,
	}: {totalItems?: number; endCursor?: string | null; hasMoreTotalItems?: boolean} = {},
): QueryAgentInstanceHistoryResponseBody {
	return createPaginatedResponse({
		items,
		page: {totalItems, startCursor: null, endCursor, hasMoreTotalItems},
	});
}

export {
	createAgentInstance,
	createQueryAgentInstancesResponse,
	createAgentInstanceHistoryItem,
	createQueryAgentInstanceHistoryResponse,
};
