/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {IS_AI_AGENT_ENABLED} from 'modules/feature-flags';
import {useProcessInstanceAgentInstances} from './useProcessInstanceAgentInstances';
import {useAgentInstancesSearch} from './useAgentInstancesSearch';

const useAgentInstanceForElement = (
  selectedElementId: string | null,
  elementInstanceKey: string | null,
) => {
  const {
    data: activeAgentInstancesResult,
    isSuccess,
    isLoading: isLoadingActive,
    isError: isErrorActive,
  } = useProcessInstanceAgentInstances();

  const cachedAgentInstance = selectedElementId
    ? activeAgentInstancesResult?.items?.find(
        (agent) => agent.elementId === selectedElementId,
      )
    : undefined;

  const needsFallback =
    IS_AI_AGENT_ENABLED &&
    isSuccess &&
    cachedAgentInstance === undefined &&
    !!elementInstanceKey;

  const {
    data: fallbackAgentInstanceResult,
    isLoading: isLoadingFallback,
    isError: isErrorFallback,
  } = useAgentInstancesSearch(
    {
      filter: {
        elementInstanceKeys: [{$eq: elementInstanceKey ?? ''}],
      },
      page: {limit: 1},
    },
    {
      enabled: needsFallback,
      refetchInterval: 5000,
    },
  );

  const agentInstance =
    cachedAgentInstance ?? fallbackAgentInstanceResult?.items?.[0];

  return {
    agentInstance,
    isLoading: isLoadingActive || (needsFallback && isLoadingFallback),
    isError: isErrorActive || (needsFallback && isErrorFallback),
  };
};

export {useAgentInstanceForElement};
