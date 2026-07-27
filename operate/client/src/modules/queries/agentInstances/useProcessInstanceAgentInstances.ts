/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import {useProcessInstancePageParams} from 'App/ProcessInstance/useProcessInstancePageParams';
import {agentInstancesSearchOptions} from './agentInstancesSearch';
import {ACTIVE_STATUSES_ARRAY} from './agentInstanceStatus';
import {useIsProcessInstanceRunning} from '../processInstance/useIsProcessInstanceRunning';

const useProcessInstanceAgentInstances = () => {
  const {processInstanceId} = useProcessInstancePageParams();
  const {data: isProcessInstanceRunning} = useIsProcessInstanceRunning();

  return useQuery({
    ...agentInstancesSearchOptions({
      loadAllItems: true,
      payload: {
        filter: {
          processInstanceKey: processInstanceId ?? '',
          status: {$in: ACTIVE_STATUSES_ARRAY},
        },
      },
    }),
    enabled: !!processInstanceId,
    refetchInterval: isProcessInstanceRunning ? 5000 : undefined,
    staleTime: 5000,
  });
};

export {useProcessInstanceAgentInstances};
