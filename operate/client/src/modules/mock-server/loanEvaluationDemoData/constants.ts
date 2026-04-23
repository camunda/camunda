/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

export const MOCK_LOAN_INSTANCE_KEY = '5551799813685000';
export const MOCK_LOAN_DEFINITION_KEY = '5551799813685099';
export const MOCK_LOAN_DEFINITION_ID = 'LoanEvaluation';
export const MOCK_LOAN_AGENT_TASK_KEY = '5551799813685010';
export const MOCK_LOAN_TOOLS_SUBPROCESS_KEY = '5551799813685020';

export const MOCK_LOAN_AGENT_ITERATION_1_KEY = '5551799813685011';
export const MOCK_LOAN_AGENT_ITERATION_2_KEY = '5551799813685012';
export const MOCK_LOAN_AGENT_ITERATION_3_KEY = '5551799813685013';
export const MOCK_LOAN_TOOLS_ITERATION_1_KEY = '5551799813685021';
export const MOCK_LOAN_TOOLS_ITERATION_2_KEY = '5551799813685022';

// All elementIds that belong to the agent task pattern (for UI detection)
export const MOCK_LOAN_AGENT_ELEMENT_IDS = new Set([
  'ai_task_agent',
  'tools',
  'get_credit_score',
  'verify_income',
  'check_risk_profile',
  'check_fraud',
  'notify_slack',
]);
