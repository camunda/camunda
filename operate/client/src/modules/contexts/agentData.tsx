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
  agentSubprocessKey: string | null;
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
  activeAgentStatuses: [],
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

    const agentEntry = scenario.agentInstances.find(
      (e) => e.instance.agentInstanceKey === agentInstance.agentInstanceKey,
    );
    if (!agentEntry) {
      return EMPTY_VALUE;
    }

    const elementData = historyToAgentElementData(
      agentInstance,
      historyResult.items,
    );
    const data: Record<string, AgentElementData> = {
      [agentEntry.elementInstanceKey]: elementData,
    };

    return {
      isAgentInstance: true,
      agentData: data,
      getAgentDataForElement: (elementInstanceKey) =>
        data[elementInstanceKey] ?? null,
      isAgentElement: (elementId) =>
        !!elementId && scenario.agentElementIds.has(elementId),
      agentSubprocessKey: agentEntry.elementInstanceKey,
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
      activeAgentStatuses: buildActiveAgentStatuses(
        elementData,
        scenario.agentElementId,
      ),
    };
  }, [agentInstance, historyResult, scenario]);

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
  const statuses: ActiveAgentStatus[] = [];
  if (elementData.status !== 'COMPLETED' && elementData.status !== 'FAILED') {
    statuses.push({
      elementId: agentElementId,
      label: 'Calling tools...',
      showShine: true,
    });
  }
  statuses.push({
    elementId: NESTED_TASK_AGENT_ELEMENT_ID,
    label: NESTED_TASK_AGENT_LABEL,
    showShine: false,
  });
  return statuses;
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
