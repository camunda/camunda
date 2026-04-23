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
import {getScenarioByInstanceKey} from 'modules/mock-server/scenarioRegistry';
import {useProcessInstanceXml} from 'modules/queries/processDefinitions/useProcessInstanceXml';
import {detectAgentElement, buildPlaceholderAgentData} from './detectAgent';

interface AgentDataContextValue {
  isAgentInstance: boolean;
  agentData: Record<string, AgentElementData> | null;
  getAgentDataForElement: (
    elementInstanceKey: string,
  ) => AgentElementData | null;
  /** Check if an elementId belongs to the agent scope. */
  isAgentElement: (elementId: string | null | undefined) => boolean;
  /** The element instance key of the agent element (subprocess or task). */
  agentSubprocessKey: string | null;
  /** The BPMN element ID of the agent element (for overlay positioning). */
  agentElementId: string | null;
  /** Get the reasoning/message preview for an LLM call elementId (e.g. "LLM_Call_1"). */
  getIterationSummary: (elementId: string) => string | null;
  /** Get the full iteration for an LLM call elementId (e.g. "LLM_Call_1"). */
  getIterationForElement: (elementId: string) => AgentIteration | null;
  /** Get a tool call + its parent iteration by tool elementId. */
  getToolCallForElement: (
    elementId: string,
  ) => {tool: AgentToolCall; iteration: AgentIteration} | null;
  /** Get the current agent status label for the diagram overlay. */
  getAgentStatusLabel: () => string | null;
}

const AgentDataContext = createContext<AgentDataContextValue>({
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
});

const AgentDataProvider: React.FC<{
  processInstanceKey: string | undefined;
  processDefinitionKey: string | undefined;
  children: React.ReactNode;
}> = ({processInstanceKey, processDefinitionKey, children}) => {
  const {data: xmlData} = useProcessInstanceXml({processDefinitionKey});

  const value = useMemo<AgentDataContextValue>(() => {
    // 1. Is this one of the scripted scenarios? If so, use its rich mock.
    const scenario = processInstanceKey
      ? getScenarioByInstanceKey(processInstanceKey)
      : undefined;

    // 2. Otherwise, detect an agent from the deployed BPMN itself.
    const detected = !scenario
      ? detectAgentElement(xmlData?.businessObjects)
      : null;

    const isAgent = scenario !== undefined || detected !== null;

    // Rich (scripted) path.
    if (scenario) {
      const data = scenario.enrichmentData;
      const agentKey = scenario.agentElementInstanceKey;
      const agentElId = scenario.agentElementId;
      const agentElementIds = scenario.agentElementIds;
      const subprocessData = data[agentKey] ?? null;
      return makeContextValue({
        isAgent,
        data,
        agentKey,
        agentElId,
        isAgentElementId: (elementId) => agentElementIds.has(elementId),
        subprocessData,
      });
    }

    // Placeholder path for real deployed agent processes.
    if (detected) {
      const placeholder = buildPlaceholderAgentData(detected);
      // Key the placeholder by the BPMN element id — we don't yet have the
      // runtime element-instance key until the backend exposes it, but
      // keying by element id is stable and enough for the prototype.
      const agentKey = detected.elementId;
      const data: Record<string, AgentElementData> = {[agentKey]: placeholder};
      const scopeIds = new Set<string>([
        detected.elementId,
        ...detected.childElementIds,
        ...detected.tools.map((t) => t.elementId),
      ]);
      return makeContextValue({
        isAgent,
        data,
        agentKey,
        agentElId: detected.elementId,
        isAgentElementId: (elementId) => scopeIds.has(elementId),
        subprocessData: placeholder,
      });
    }

    return makeContextValue({
      isAgent: false,
      data: null,
      agentKey: null,
      agentElId: null,
      isAgentElementId: () => false,
      subprocessData: null,
    });
  }, [processInstanceKey, xmlData]);

  return (
    <AgentDataContext.Provider value={value}>
      {children}
    </AgentDataContext.Provider>
  );
};

function makeContextValue(args: {
  isAgent: boolean;
  data: Record<string, AgentElementData> | null;
  agentKey: string | null;
  agentElId: string | null;
  isAgentElementId: (elementId: string) => boolean;
  subprocessData: AgentElementData | null;
}): AgentDataContextValue {
  const {isAgent, data, agentKey, agentElId, isAgentElementId, subprocessData} =
    args;

  return {
    isAgentInstance: isAgent,
    agentData: data,
    getAgentDataForElement: (elementInstanceKey: string) =>
      data?.[elementInstanceKey] ?? null,
    isAgentElement: (elementId: string | null | undefined) => {
      if (!isAgent || !elementId) {
        return false;
      }
      return isAgentElementId(elementId);
    },
    agentSubprocessKey: agentKey,
    agentElementId: agentElId,
    getIterationSummary: (elementId: string) => {
      if (!subprocessData) {
        return null;
      }
      const match = elementId.match(/^LLM_Call_(\d+)$/);
      if (!match) {
        return null;
      }
      const iterNum = parseInt(match[1]!, 10);
      const iteration = subprocessData.iterations.find(
        (it) => it.iterationNumber === iterNum,
      );
      if (!iteration) {
        return null;
      }
      return iteration.reasoning;
    },
    getIterationForElement: (elementId: string) => {
      if (!subprocessData) {
        return null;
      }
      const match = elementId.match(/^LLM_Call_(\d+)$/);
      if (!match) {
        return null;
      }
      const iterNum = parseInt(match[1]!, 10);
      return (
        subprocessData.iterations.find(
          (it) => it.iterationNumber === iterNum,
        ) ?? null
      );
    },
    getToolCallForElement: (elementId: string) => {
      if (!subprocessData) {
        return null;
      }
      for (const iteration of subprocessData.iterations) {
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
      if (!subprocessData) {
        return null;
      }
      if (
        subprocessData.status === 'COMPLETED' ||
        subprocessData.status === 'FAILED'
      ) {
        return null;
      }
      // One generic label on the canvas — multiple element instances may be
      // doing different things simultaneously, so the specific per-state
      // label belongs in the Details-tab Status accordion, not on the canvas.
      return 'Calling tools...';
    },
  };
}

function useAgentData(): AgentDataContextValue {
  return useContext(AgentDataContext);
}

export {AgentDataContext, AgentDataProvider, useAgentData};
