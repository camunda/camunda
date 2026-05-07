/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

export const MOCK_AGENT_INSTANCE_KEY = '4451799813685000';
export const MOCK_AGENT_DEFINITION_KEY = '4451799813685099';
export const MOCK_AGENT_DEFINITION_ID = 'ai-agent-chat-with-tools';
export const MOCK_AGENT_SUBPROCESS_KEY = '4451799813685010';

// One AD_HOC_SUB_PROCESS_INNER_INSTANCE wrapper per tool activation, matching
// how the engine renders ad-hoc subprocess child activations in real Operate.
export const MOCK_AGENT_INNER_INSTANCE_1_KEY = '4451799813685015';
export const MOCK_AGENT_INNER_INSTANCE_2_KEY = '4451799813685016';
export const MOCK_AGENT_INNER_INSTANCE_3_KEY = '4451799813685017';
export const MOCK_AGENT_INNER_INSTANCE_4_KEY = '4451799813685018';

// All elementIds that belong to the agent subprocess (for UI detection)
export const MOCK_AGENT_SUBPROCESS_ELEMENT_IDS = new Set([
  'AI_Agent',
  'ListUsers',
  'LoadUserByID',
  'GetDateAndTime',
  'AskHumanToSendEmail',
]);

export const MOCK_AGENT_AGENT_INSTANCE_KEY = '4451799813685200';
