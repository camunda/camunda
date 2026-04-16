/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

// Business Value dashboard related types
export interface BusinessValueKpi {
  totalValueCreated: number;
  totalBaselineCostSaved: number;
  totalLlmCost: number;
  totalAutomationCost: number;
  totalCost: number;
  platformRoi: number;
  totalTokenUsage: number;
  completedInstances: number;
  activeProcesses: number;
  automationRate: number;
  agentTaskCount: number;
  humanTaskCount: number;
  autoTaskCount: number;
}

export interface TopProcess {
  processDefinitionKey: string;
  processLabel: string;
  valueCreated: number;
  baselineCostSaved: number;
  llmCost: number;
  instanceCount: number;
}

export interface TrendEntry {
  month: string;
  valueCreated: number;
  baselineCostSaved: number;
  llmCost: number;
}

export interface AgentTask {
  agentName: string;
  invocationCount: number;
  totalCost: number;
  tokenUsage: number;
}

export interface ModelCost {
  modelName: string;
  totalCost: number;
  tokenUsage: number;
  invocationCount: number;
}

export interface BusinessValueSummary {
  kpis: BusinessValueKpi;
  topProcesses: TopProcess[];
  trend: TrendEntry[];
  topAgentTasks: AgentTask[];
  costByModel: ModelCost[];
}

export interface BusinessValueFilter {
  startDate: string;
  endDate: string;
  tenantId?: string;
}
