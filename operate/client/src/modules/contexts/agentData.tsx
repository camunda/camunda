/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createContext, useContext, useMemo} from 'react';
import {useAgentInstanceHistory} from 'modules/queries/agentInstances/useAgentInstanceHistory';
import {useSearchAgentInstances} from 'modules/queries/agentInstances/useSearchAgentInstances';
import {historyToAgentElementData} from 'modules/queries/agentInstances/historyToAgentElementData';
import {getScenarioByInstanceKey} from 'modules/mock-server/scenarioRegistry';
import type {
  AgentElementData,
  AgentIteration,
  AgentToolCall,
} from './agentData.types';

interface AgentDataContextValue {
  isAgentInstance: boolean;
  agentData: Record<string, AgentElementData> | null;
  getAgentDataForElement: (
    elementInstanceKey: string,
  ) => AgentElementData | null;
  isAgentElement: (elementId: string | null | undefined) => boolean;
  agentSubprocessKey: string | null;
  agentElementId: string | null;
  getIterationSummary: (elementId: string) => string | null;
  getIterationForElement: (elementId: string) => AgentIteration | null;
  getToolCallForElement: (
    elementId: string,
  ) => {tool: AgentToolCall; iteration: AgentIteration} | null;
  getAgentStatusLabel: () => string | null;
}

const EMPTY_VALUE: AgentDataContextValue = {
  isAgentInstance: false,
  agentData: null,
  getAgentDataForElement: () => null,
  isAgentElement: () => false,
  agentSubprocessKey: null,
  agentElementId: null,
  getIterationSummary: () => null,
  getIterationForElement: () => null,
  getToolCallForElement: () => null,
  getAgentStatusLabel: () => null,
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
  const agentInstance = agentInstancesResult?.items[0];

  const {data: historyResult} = useAgentInstanceHistory(
    agentInstance?.agentInstanceKey,
    {filter: {committed: true}},
  );

  const value = useMemo<AgentDataContextValue>(() => {
    if (!agentInstance || !historyResult || !scenario) {
      return EMPTY_VALUE;
    }

    const elementData = historyToAgentElementData(
      agentInstance,
      historyResult.items,
    );
    const data: Record<string, AgentElementData> = {
      [scenario.agentElementInstanceKey]: elementData,
    };

    return {
      isAgentInstance: true,
      agentData: data,
      getAgentDataForElement: (elementInstanceKey) =>
        data[elementInstanceKey] ?? null,
      isAgentElement: (elementId) =>
        !!elementId && scenario.agentElementIds.has(elementId),
      agentSubprocessKey: scenario.agentElementInstanceKey,
      agentElementId: scenario.agentElementId,
      getIterationSummary: (elementId) => {
        const iteration = matchIteration(elementData, elementId);
        return iteration?.reasoning ?? null;
      },
      getIterationForElement: (elementId) =>
        matchIteration(elementData, elementId),
      getToolCallForElement: (elementId) => {
        for (const iteration of elementData.iterations) {
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
        if (
          elementData.status === 'COMPLETED' ||
          elementData.status === 'FAILED'
        ) {
          return null;
        }
        return 'Calling tools...';
      },
    };
  }, [agentInstance, historyResult, scenario]);

  return (
    <AgentDataContext.Provider value={value}>
      {children}
    </AgentDataContext.Provider>
  );
};

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
