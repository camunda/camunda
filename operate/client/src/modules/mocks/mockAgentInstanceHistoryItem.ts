/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {AgentInstanceHistoryItem} from '@camunda/camunda-api-zod-schemas/8.10';

function mockAgentInstanceHistoryItem(
  overwrites: Partial<AgentInstanceHistoryItem> = {},
): AgentInstanceHistoryItem {
  return {
    historyItemKey: 'history-item-1',
    agentInstanceKey: 'agent-instance-1',
    elementInstanceKey: 'elem-1',
    jobKey: 'job-1',
    jobLease: 'lease-1',
    iteration: 1,
    role: 'ASSISTANT',
    content: [{contentType: 'TEXT', text: 'Hello from assistant.'}],
    toolCalls: [],
    metrics: null,
    commitStatus: 'COMMITTED',
    producedAt: '2025-01-15T10:00:00.000Z',
    ...overwrites,
  };
}

export {mockAgentInstanceHistoryItem};
