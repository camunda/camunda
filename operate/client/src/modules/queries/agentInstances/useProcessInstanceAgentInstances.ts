/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useProcessInstancePageParams} from 'App/ProcessInstance/useProcessInstancePageParams';
import {useAgentInstancesSearch} from './useAgentInstancesSearch';
import {ACTIVE_STATUSES_ARRAY} from './agentInstanceStatus';

const useProcessInstanceAgentInstances = () => {
  const {processInstanceId} = useProcessInstancePageParams();

  return useAgentInstancesSearch(
    {
      filter: {
        processInstanceKey: processInstanceId ?? '',
        status: {$in: ACTIVE_STATUSES_ARRAY},
      },
    },
    {
      enabled: !!processInstanceId,
      refetchInterval: 5000,
      loadAllItems: true,
    },
  );
};

export {useProcessInstanceAgentInstances};
