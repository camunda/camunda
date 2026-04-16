/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {BusinessValueSummary} from './types';

export const BUSINESS_VALUE_FIXTURE: BusinessValueSummary = {
  kpis: {
    totalValueCreated: 142_800,
    totalBaselineCostSaved: 98_400,
    totalLlmCost: 27_400,
    totalAutomationCost: 17_000,
    totalCost: 44_400,
    platformRoi: 5.2,
    totalTokenUsage: 17_000_000,
    completedInstances: 42_000,
    activeProcesses: 3,
    automationRate: 0.68,
    agentTaskCount: 12_600,
    humanTaskCount: 13_440,
    autoTaskCount: 15_960,
  },
  topProcesses: [
    {
      processDefinitionKey: 'order-fulfillment',
      processLabel: 'Order Fulfillment',
      valueCreated: 62_400,
      baselineCostSaved: 43_200,
      llmCost: 9_800,
      instanceCount: 18_200,
    },
    {
      processDefinitionKey: 'invoice-processing',
      processLabel: 'Invoice Processing',
      valueCreated: 48_200,
      baselineCostSaved: 33_400,
      llmCost: 8_400,
      instanceCount: 14_100,
    },
    {
      processDefinitionKey: 'employee-onboarding',
      processLabel: 'Employee Onboarding',
      valueCreated: 32_200,
      baselineCostSaved: 21_800,
      llmCost: 9_200,
      instanceCount: 9_700,
    },
  ],
  trend: [
    {month: '2025-09', valueCreated: 18_200, baselineCostSaved: 12_600, llmCost: 4_100},
    {month: '2025-10', valueCreated: 22_400, baselineCostSaved: 15_500, llmCost: 4_900},
    {month: '2025-11', valueCreated: 28_100, baselineCostSaved: 19_400, llmCost: 6_100},
    {month: '2025-12', valueCreated: 35_600, baselineCostSaved: 24_600, llmCost: 7_800},
    {month: '2026-01', valueCreated: 38_500, baselineCostSaved: 26_300, llmCost: 4_500},
  ],
  topAgentTasks: [
    {
      agentName: 'Risk Analysis Agent',
      invocationCount: 850_000,
      totalCost: 8_000,
      tokenUsage: 5_200_000,
    },
    {
      agentName: 'Document Classification',
      invocationCount: 620_000,
      totalCost: 6_000,
      tokenUsage: 3_800_000,
    },
    {
      agentName: 'Fraud Detection',
      invocationCount: 530_000,
      totalCost: 5_000,
      tokenUsage: 3_100_000,
    },
  ],
  costByModel: [
    {modelName: 'GPT-4', totalCost: 11_000, tokenUsage: 7_000_000, invocationCount: 800_000},
    {modelName: 'Claude', totalCost: 6_000, tokenUsage: 5_500_000, invocationCount: 600_000},
    {modelName: 'Gemini', totalCost: 3_000, tokenUsage: 4_500_000, invocationCount: 700_000},
  ],
};
