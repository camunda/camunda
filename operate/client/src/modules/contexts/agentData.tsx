/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createContext, useContext, useMemo} from 'react';
import {useQueries} from '@tanstack/react-query';
import {searchAgentInstanceHistory} from 'modules/api/v2/agentInstances/searchAgentInstanceHistory';
import {useSearchAgentInstances} from 'modules/queries/agentInstances/useSearchAgentInstances';
import {historyToAgentElementData} from 'modules/queries/agentInstances/historyToAgentElementData';
import {getScenarioByInstanceKey} from 'modules/mock-server/scenarioRegistry';
import type {ScenarioDefinition} from 'modules/mock-server/scenarioRegistry';
import type {
  AgentElementData,
  AgentIteration,
  AgentToolCall,
} from './agentData.types';

type ActiveAgentStatus = {
  elementId: string;
  label: string;
  // Whether the canvas should render the animated shine border around this
  // element. The status tag always renders; the shine is opt-in so nested
  // agent tasks can show a status without also pulsing.
  showShine: boolean;
};

interface AgentDataContextValue {
  isAgentInstance: boolean;
  agentData: Record<string, AgentElementData> | null;
  getAgentDataForElement: (
    elementInstanceKey: string,
  ) => AgentElementData | null;
  isAgentElement: (elementId: string | null | undefined) => boolean;
  agentElementInstanceKeys: string[];
  primaryAgentElementInstanceKey: string | null;
  agentElementId: string | null;
  getIterationSummary: (elementId: string) => string | null;
  getIterationForElement: (elementId: string) => AgentIteration | null;
  getToolCallForElement: (
    elementId: string,
  ) => {tool: AgentToolCall; iteration: AgentIteration} | null;
  getAgentStatusLabel: () => string | null;
  // One entry per BPMN element that should render a status tag + shine border.
  // Consumers (TopPanel overlays) iterate this to support multiple in-flight
  // agents on the canvas — e.g. an orchestrating agent and a nested AI task
  // agent both showing their state at once.
  activeAgentStatuses: ActiveAgentStatus[];
  variant: ScenarioDefinition['variant'];
}

const EMPTY_VALUE: AgentDataContextValue = {
  isAgentInstance: false,
  agentData: null,
  getAgentDataForElement: () => null,
  isAgentElement: () => false,
  agentElementInstanceKeys: [],
  primaryAgentElementInstanceKey: null,
  agentElementId: null,
  getIterationSummary: () => null,
  getIterationForElement: () => null,
  getToolCallForElement: () => null,
  getAgentStatusLabel: () => null,
  activeAgentStatuses: [],
  variant: undefined,
};

const AgentDataContext = createContext<AgentDataContextValue>(EMPTY_VALUE);

const AgentDataProvider: React.FC<{
  processInstanceKey: string | undefined;
  processDefinitionKey: string | undefined;
  children: React.ReactNode;
}> = ({processInstanceKey, children}) => {
  // Look up the scenario to know which BPMN element ids belong to the agent
  // scope (used by `isAgentElement`). The scenario itself does NOT supply the
  // agent data — that comes from the queries below, via MSW handlers that read
  // from the same fixtures.
  const scenario = processInstanceKey
    ? getScenarioByInstanceKey(processInstanceKey)
    : undefined;

  const {data: agentInstancesResult} = useSearchAgentInstances(
    {filter: {processInstanceKey: processInstanceKey ?? ''}},
    {enabled: !!processInstanceKey},
  );
  const allAgentInstances = agentInstancesResult?.items ?? [];

  const historyResults = useQueries({
    queries: allAgentInstances.map((instance) => ({
      queryKey: [
        'agentInstanceHistory',
        instance.agentInstanceKey,
        {committed: true},
      ],
      queryFn: async () => {
        const {response, error} = await searchAgentInstanceHistory(
          instance.agentInstanceKey,
          {filter: {committed: true}},
        );
        if (error) throw error;
        return response;
      },
    })),
  });

  const value = useMemo<AgentDataContextValue>(() => {
    if (!scenario || allAgentInstances.length === 0) {
      return EMPTY_VALUE;
    }

    const data: Record<string, AgentElementData> = {};
    for (let i = 0; i < allAgentInstances.length; i++) {
      const instance = allAgentInstances[i]!;
      const historyItems = historyResults[i]?.data?.items;
      if (!historyItems) continue;
      const scenarioEntry = scenario.agentInstances.find(
        (e) => e.instance.agentInstanceKey === instance.agentInstanceKey,
      );
      if (!scenarioEntry) continue;
      data[scenarioEntry.elementInstanceKey] = historyToAgentElementData(
        instance,
        historyItems,
      );
    }

    if (Object.keys(data).length === 0) {
      return EMPTY_VALUE;
    }

    const agentElementInstanceKeys = scenario.agentInstances.map(
      (e) => e.elementInstanceKey,
    );
    // Prefer an active run, else the last entry.
    const activeEntry = scenario.agentInstances.find(
      (e) =>
        e.instance.status !== 'COMPLETED' && e.instance.status !== 'FAILED',
    );
    const primaryAgentElementInstanceKey =
      activeEntry?.elementInstanceKey ??
      scenario.agentInstances[scenario.agentInstances.length - 1]
        ?.elementInstanceKey ??
      null;

    const activeAgentData =
      primaryAgentElementInstanceKey !== null
        ? (data[primaryAgentElementInstanceKey] ?? null)
        : null;

    return {
      isAgentInstance: true,
      agentData: data,
      getAgentDataForElement: (elementInstanceKey) =>
        data[elementInstanceKey] ?? null,
      isAgentElement: (elementId) =>
        !!elementId && scenario.agentElementIds.has(elementId),
      agentElementInstanceKeys,
      primaryAgentElementInstanceKey,
      agentElementId: scenario.agentElementId,
      getIterationSummary: (elementId) => {
        if (!activeAgentData) return null;
        const iteration = matchIteration(activeAgentData, elementId);
        return iteration?.reasoning ?? null;
      },
      getIterationForElement: (elementId) =>
        activeAgentData ? matchIteration(activeAgentData, elementId) : null,
      getToolCallForElement: (elementId) => {
        if (!activeAgentData) return null;
        for (const iteration of activeAgentData.iterations) {
          const tool = iteration.toolCalls.find(
            (t) => t.toolElementId === elementId,
          );
          if (tool) {
            return {tool, iteration};
          }
        }
        return null;
      },
      getAgentStatusLabel: () => {
        if (!activeAgentData) return null;
        if (
          activeAgentData.status === 'COMPLETED' ||
          activeAgentData.status === 'FAILED'
        ) {
          return null;
        }
        return 'Calling tools...';
      },
      activeAgentStatuses: activeAgentData
        ? buildActiveAgentStatuses(activeAgentData, scenario.agentElementId)
        : [],
      variant: scenario.variant,
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [agentInstancesResult, historyResults, scenario]);

  return (
    <AgentDataContext.Provider value={value}>
      {children}
    </AgentDataContext.Provider>
  );
};

// Demo wiring: in the prototype scenario a nested "AI task agent" tool runs
// inside the orchestrating agent. Surface it as a second active status so
// the canvas shows both tags at once. Replace with real per-agent state once
// the API exposes nested agent instances.
const NESTED_TASK_AGENT_ELEMENT_ID = 'AI_Task_Agent';
const NESTED_TASK_AGENT_LABEL = 'Thinking...';

function buildActiveAgentStatuses(
  elementData: AgentElementData,
  agentElementId: string,
): ActiveAgentStatus[] {
  const isLive =
    elementData.status !== 'COMPLETED' && elementData.status !== 'FAILED';
  if (!isLive) return [];
  return [
    {
      elementId: agentElementId,
      label: 'Calling tools...',
      showShine: true,
    },
    {
      elementId: NESTED_TASK_AGENT_ELEMENT_ID,
      label: NESTED_TASK_AGENT_LABEL,
      showShine: false,
    },
  ];
}

function matchIteration(
  elementData: AgentElementData,
  elementId: string,
): AgentIteration | null {
  const match = elementId.match(/^LLM_Call_(\d+)$/);
  if (!match) {
    return null;
  }
  const iterNum = parseInt(match[1]!, 10);
  return (
    elementData.iterations.find((it) => it.iterationNumber === iterNum) ?? null
  );
}

function useAgentData(): AgentDataContextValue {
  return useContext(AgentDataContext);
}

export {AgentDataContext, AgentDataProvider, useAgentData};
