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
import type {AgentStatusPayload} from 'modules/bpmn-js/overlayTypes';

type AgentInstancesStatusMap = Map<
  AgentInstance['elementId'],
  AgentStatusPayload
>;

const useAgentInstancesStatusPerElement = (): {
  agentInstancesStatusMap: AgentInstancesStatusMap;
  elementsWithAgent: Set<string>;
} => {
  const {data} = useProcessInstanceAgentInstances();

  return useMemo(() => {
    const agentInstancesStatusMap: AgentInstancesStatusMap = new Map();

    for (const agentInstance of data?.items ?? []) {
      const statusInfo = agentInstancesStatusMap.get(agentInstance.elementId);
      if (!statusInfo) {
        agentInstancesStatusMap.set(agentInstance.elementId, {
          agentInstanceKey: agentInstance.agentInstanceKey,
          status: agentInstance.status,
          additionalActiveCount: 0,
        });
      } else {
        // NOTE: Object stored in map. So data in map gets updated as well.
        statusInfo.additionalActiveCount += 1;
      }
    }

    return {
      agentInstancesStatusMap,
      elementsWithAgent: new Set(agentInstancesStatusMap.keys()),
    };
  }, [data]);
};

export {type AgentInstancesStatusMap, useAgentInstancesStatusPerElement};
