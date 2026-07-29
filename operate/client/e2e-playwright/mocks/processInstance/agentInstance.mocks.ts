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
  QueryAgentInstancesResponseBody,
  QueryAgentInstanceHistoryResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.10';

// IDs are aligned with the `runningInstance` mock so the agent instance is
// associated with its "Signal user task" element (elementId Activity_0dex012).
const AGENT_INSTANCE_KEY = '2251799813851828';
const PROCESS_INSTANCE_KEY = '2251799813687144';
const ELEMENT_INSTANCE_KEY = '2251799813687150';

const agentInstance: AgentInstance = {
  agentInstanceKey: AGENT_INSTANCE_KEY,
  status: 'THINKING',
  definition: {
    model: 'gpt-4o',
    provider: 'openai',
    systemPrompt: 'You are a helpful assistant that answers support questions.',
  },
  metrics: {
    inputTokens: 1200,
    outputTokens: 800,
    modelCalls: 3,
    toolCalls: 2,
  },
  limits: {
    maxModelCalls: 10,
    maxToolCalls: 20,
    maxTokens: 5000,
  },
  tools: [
    {name: 'searchKnowledgeBase', description: 'Search the knowledge base'},
    {name: 'createTicket', description: 'Create a support ticket'},
  ],
  elementId: 'Activity_0dex012',
  processInstanceKey: PROCESS_INSTANCE_KEY,
  rootProcessInstanceKey: PROCESS_INSTANCE_KEY,
  processDefinitionKey: '2251799813686165',
  processDefinitionId: 'signalEventProcess',
  processDefinitionVersion: 1,
  processDefinitionVersionTag: null,
  tenantId: '<default>',
  creationDate: '2025-01-15T10:00:00.000Z',
  lastUpdatedDate: '2025-01-15T10:05:00.000Z',
  completionDate: null,
  elementInstanceKeys: [ELEMENT_INSTANCE_KEY],
};

const agentInstancesResponse: QueryAgentInstancesResponseBody = {
  items: [agentInstance],
  page: {
    totalItems: 1,
    startCursor: null,
    endCursor: null,
    hasMoreTotalItems: false,
  },
};

function historyItem(
  overrides: Partial<AgentInstanceHistoryItem>,
): AgentInstanceHistoryItem {
  return {
    historyItemKey: 'history-item-1',
    agentInstanceKey: AGENT_INSTANCE_KEY,
    elementInstanceKey: ELEMENT_INSTANCE_KEY,
    jobKey: 'job-1',
    jobLease: 'lease-1',
    loopIteration: 1,
    role: 'ASSISTANT',
    content: [{contentType: 'TEXT', text: 'Placeholder message.'}],
    toolCalls: [],
    metrics: null,
    commitStatus: 'COMMITTED',
    producedAt: '2025-01-15T10:00:00.000Z',
    ...overrides,
  };
}

// Ordered newest-first so the ASSISTANT message is items[0] (consumed by the
// "latest agent message" query, which requests a single ASSISTANT item).
const agentHistoryResponse: QueryAgentInstanceHistoryResponseBody = {
  items: [
    historyItem({
      historyItemKey: 'history-item-3',
      role: 'ASSISTANT',
      loopIteration: 2,
      content: [
        {
          contentType: 'TEXT',
          text: 'I found the answer in the knowledge base. Preparing a response.',
        },
      ],
      producedAt: '2025-01-15T10:05:00.000Z',
    }),
    historyItem({
      historyItemKey: 'history-item-2',
      role: 'USER',
      loopIteration: 1,
      content: [{contentType: 'TEXT', text: 'How do I reset my password?'}],
      producedAt: '2025-01-15T10:01:00.000Z',
    }),
    historyItem({
      historyItemKey: 'history-item-1',
      role: 'ASSISTANT',
      loopIteration: 1,
      content: [{contentType: 'TEXT', text: 'Let me look that up for you.'}],
      producedAt: '2025-01-15T10:00:00.000Z',
    }),
  ],
  page: {
    totalItems: 3,
    startCursor: null,
    endCursor: null,
    hasMoreTotalItems: false,
  },
};

export {agentInstance, agentInstancesResponse, agentHistoryResponse};
