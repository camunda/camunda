/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createContext, useContext, useMemo} from 'react';
import type {
  AgentElementData,
  AgentIteration,
  AgentToolCall,
} from 'modules/mock-server/agentDemoData';
import {
  isAgentDemoInstance,
  MOCK_AGENT_ENRICHMENT_DATA,
  MOCK_AGENT_SUBPROCESS_KEY,
  MOCK_AGENT_SUBPROCESS_ELEMENT_IDS,
} from 'modules/mock-server/agentDemoData';

interface AgentDataContextValue {
  isAgentInstance: boolean;
  agentData: Record<string, AgentElementData> | null;
  getAgentDataForElement: (
    elementInstanceKey: string,
  ) => AgentElementData | null;
  /** Check if an elementId belongs to the agent ad-hoc subprocess or its children */
  isAgentElement: (elementId: string | null | undefined) => boolean;
  /** The element instance key of the agent ad-hoc subprocess */
  agentSubprocessKey: string | null;
  /** Get the reasoning/message preview for an LLM call elementId (e.g. "LLM_Call_1") */
  getIterationSummary: (elementId: string) => string | null;
  /** Get the full iteration for an LLM call elementId (e.g. "LLM_Call_1") */
  getIterationForElement: (elementId: string) => AgentIteration | null;
  /** Get a tool call + its parent iteration by tool elementId (e.g. "ListUsers") */
  getToolCallForElement: (
    elementId: string,
  ) => {tool: AgentToolCall; iteration: AgentIteration} | null;
  /** Get the current agent status label for diagram overlay */
  getAgentStatusLabel: () => string | null;
}

const AgentDataContext = createContext<AgentDataContextValue>({
  isAgentInstance: false,
  agentData: null,
  getAgentDataForElement: () => null,
  isAgentElement: () => false,
  agentSubprocessKey: null,
  getIterationSummary: () => null,
  getIterationForElement: () => null,
  getToolCallForElement: () => null,
  getAgentStatusLabel: () => null,
});

const AgentDataProvider: React.FC<{
  processInstanceKey: string | undefined;
  children: React.ReactNode;
}> = ({processInstanceKey, children}) => {
  const value = useMemo<AgentDataContextValue>(() => {
    const isAgent = processInstanceKey
      ? isAgentDemoInstance(processInstanceKey)
      : false;

    const data = isAgent ? MOCK_AGENT_ENRICHMENT_DATA : null;

    const subprocessData = data?.[MOCK_AGENT_SUBPROCESS_KEY] ?? null;

    return {
      isAgentInstance: isAgent,
      agentData: data,
      getAgentDataForElement: (elementInstanceKey: string) =>
        data?.[elementInstanceKey] ?? null,
      isAgentElement: (elementId: string | null | undefined) => {
        if (!isAgent || !elementId) return false;
        return MOCK_AGENT_SUBPROCESS_ELEMENT_IDS.has(elementId);
      },
      agentSubprocessKey: isAgent ? MOCK_AGENT_SUBPROCESS_KEY : null,
      getIterationSummary: (elementId: string) => {
        if (!subprocessData) return null;
        const match = elementId.match(/^LLM_Call_(\d+)$/);
        if (!match) return null;
        const iterNum = parseInt(match[1]!, 10);
        const iteration = subprocessData.iterations.find(
          (it) => it.iterationNumber === iterNum,
        );
        if (!iteration) return null;
        return iteration.reasoning;
      },
      getIterationForElement: (elementId: string) => {
        if (!subprocessData) return null;
        const match = elementId.match(/^LLM_Call_(\d+)$/);
        if (!match) return null;
        const iterNum = parseInt(match[1]!, 10);
        return (
          subprocessData.iterations.find(
            (it) => it.iterationNumber === iterNum,
          ) ?? null
        );
      },
      getToolCallForElement: (elementId: string) => {
        if (!subprocessData) return null;
        for (const iteration of subprocessData.iterations) {
          const tool = iteration.toolCalls.find(
            (t) => t.toolElementId === elementId,
          );
          if (tool) return {tool, iteration};
        }
        return null;
      },
      getAgentStatusLabel: () => {
        if (!subprocessData) return null;
        // Find active tool calls across all iterations
        const activeTools: AgentToolCall[] = [];
        for (const iteration of subprocessData.iterations) {
          for (const tool of iteration.toolCalls) {
            if (tool.status === 'ACTIVE') activeTools.push(tool);
          }
        }
        if (activeTools.length > 0) {
          const count = activeTools.length;
          const toolName = activeTools[0]!.toolName;
          return `Calling ${count} tool${count > 1 ? 's' : ''}: ${toolName}`;
        }
        const statusLabels: Record<string, string> = {
          INITIALIZING: 'Initializing',
          TOOL_DISCOVERY: 'Discovering tools',
          THINKING: 'Thinking',
          WAITING_FOR_TOOL: 'Waiting for tool',
          COMPLETED: 'Completed',
          FAILED: 'Failed',
        };
        return statusLabels[subprocessData.status] ?? null;
      },
    };
  }, [processInstanceKey]);

  return (
    <AgentDataContext.Provider value={value}>
      {children}
    </AgentDataContext.Provider>
  );
};

function useAgentData(): AgentDataContextValue {
  return useContext(AgentDataContext);
}

export {AgentDataContext, AgentDataProvider, useAgentData};
