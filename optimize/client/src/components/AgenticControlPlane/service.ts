/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {get} from 'request';

export async function loadAgenticDashboard() {
  const response = await get('api/dashboard/agentic');
  return response.json();
}

export interface AgentOption {
  agentId: string;
  name: string;
  processDefinitionKey: string;
}

export interface ModelOption {
  model: string;
  provider: string | null;
}

export interface AgenticFilterOptions {
  agents: AgentOption[];
  models: ModelOption[];
}

export async function loadAgenticFilterOptions(
  processDefinitionKey: string | null
): Promise<AgenticFilterOptions> {
  const response = await get(
    'api/dashboard/agentic/filter-options',
    processDefinitionKey ? {processDefinitionKey} : {}
  );
  return response.json();
}
