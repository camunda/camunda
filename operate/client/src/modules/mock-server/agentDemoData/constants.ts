/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

export const MOCK_AGENT_INSTANCE_KEY_ACTIVE = '4451799813685000';
export const MOCK_AGENT_DEFINITION_KEY_ACTIVE = '4451799813685099';
export const MOCK_AGENT_DEFINITION_ID_ACTIVE = 'ai-agent-chat-with-tools';
export const MOCK_AGENT_SUBPROCESS_KEY_ACTIVE = '4451799813685010';

// One AD_HOC_SUB_PROCESS_INNER_INSTANCE wrapper per tool activation, matching
// how the engine renders ad-hoc subprocess child activations in real Operate.
export const MOCK_AGENT_INNER_INSTANCE_1_KEY_ACTIVE = '4451799813685015';
export const MOCK_AGENT_INNER_INSTANCE_2_KEY_ACTIVE = '4451799813685016';
export const MOCK_AGENT_INNER_INSTANCE_3_KEY_ACTIVE = '4451799813685017';
export const MOCK_AGENT_INNER_INSTANCE_4_KEY_ACTIVE = '4451799813685018';
export const MOCK_AGENT_INNER_INSTANCE_5_KEY_ACTIVE = '4451799813685019';
export const MOCK_AGENT_TASK_AGENT_INSTANCE_KEY_ACTIVE = '4451799813685045';

// All elementIds that belong to the agent subprocess (for UI detection)
export const MOCK_AGENT_SUBPROCESS_ELEMENT_IDS_ACTIVE = new Set([
  'AI_Agent',
  'AI_Task_Agent',
  'ListUsers',
  'LoadUserByID',
  'GetDateAndTime',
  'AskHumanToSendEmail',
]);

export const MOCK_AGENT_AGENT_INSTANCE_KEY_ACTIVE = '4451799813685200';

// State 1 — Agent not yet active. No AI_Agent instance; only the entry events.
export const MOCK_AGENT_INSTANCE_KEY_NOT_ACTIVE = '5451799813685000';
export const MOCK_AGENT_DEFINITION_KEY_NOT_ACTIVE = '5451799813685099';
export const MOCK_AGENT_DEFINITION_ID_NOT_ACTIVE = 'ai-agent-chat-with-tools';
