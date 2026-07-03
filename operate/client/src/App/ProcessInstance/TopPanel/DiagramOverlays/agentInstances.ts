/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo} from 'react';
import {useProcessInstanceAgentInstances} from 'modules/queries/agentInstances/useProcessInstanceAgentInstances';
import type {AgentInstance} from '@camunda/camunda-api-zod-schemas/8.10';

/**
 * Shared by the agent overlay modules and the waiting-state module.
 *
 * We expect only one active agent instance per element, but there *can* be
 * multiple. For now we only keep the first agent instance per element, so each
 * element gets at most one set of agent overlays.
 */
const useFirstAgentInstancePerElement = (): {
  agentInstances: AgentInstance[];
  elementsWithAgent: Set<string>;
} => {
  const {data} = useProcessInstanceAgentInstances();

  return useMemo(() => {
    const elementsWithAgent = new Set<string>();
    const agentInstances: AgentInstance[] = [];

    for (const agentInstance of data?.items ?? []) {
      if (elementsWithAgent.has(agentInstance.elementId)) {
        continue;
      }
      elementsWithAgent.add(agentInstance.elementId);
      agentInstances.push(agentInstance);
    }

    return {agentInstances, elementsWithAgent};
  }, [data]);
};

export {useFirstAgentInstancePerElement};
