/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {AgentInstance} from '@camunda/camunda-api-zod-schemas/8.10';

function mockAgentInstance(
  overwrites: Partial<AgentInstance> = {},
): AgentInstance {
  return {
    agentInstanceKey: '2251799813851828',
    status: 'TOOL_CALLING',
    definition: {
      model: 'gpt-4',
      provider: 'openai',
      systemPrompt: 'You are a helpful assistant.',
    },
    metrics: {
      inputTokens: 100,
      outputTokens: 50,
      modelCalls: 3,
      toolCalls: 2,
    },
    limits: {
      maxModelCalls: 10,
      maxToolCalls: 5,
      maxTokens: 1000,
    },
    tools: [],
    elementId: 'Activity_1',
    processInstanceKey: '123456789',
    rootProcessInstanceKey: '123456789',
    processDefinitionKey: '444555666',
    processDefinitionId: 'process-def-1',
    processDefinitionVersion: 1,
    processDefinitionVersionTag: null,
    tenantId: '<default>',
    creationDate: '2025-01-15T10:00:00.000Z',
    lastUpdatedDate: '2025-01-15T10:05:00.000Z',
    completionDate: null,
    elementInstanceKeys: ['111222333'],
    ...overwrites,
  };
}

export {mockAgentInstance};
