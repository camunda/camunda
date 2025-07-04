/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useProcessInstance} from 'modules/queries/processInstance/useProcessInstance';
import {
  type FlowNodeInstance,
  flowNodeInstanceStore,
} from 'modules/stores/flowNodeInstance';

const useIsInstanceExecutionHistoryAvailable = () => {
  const {status} = flowNodeInstanceStore.state;
  const instanceExecutionHistory = useInstanceExecutionHistory();

  return (
    ['fetched', 'fetching-next', 'fetching-prev'].includes(status) &&
    instanceExecutionHistory !== null &&
    Object.keys(instanceExecutionHistory).length > 0
  );
};

const useInstanceExecutionHistory = (): FlowNodeInstance | null => {
  const {data: processInstance} = useProcessInstance();
  const {status} = flowNodeInstanceStore.state;

  if (!processInstance || ['initial', 'first-fetch'].includes(status)) {
    return null;
  }

  return {
    id: processInstance.processInstanceKey,
    type: 'PROCESS',
    state: processInstance.hasIncident ? 'INCIDENT' : processInstance.state,
    treePath: processInstance.processInstanceKey,
    endDate: null,
    startDate: '',
    sortValues: [],
    flowNodeId: processInstance.processDefinitionId,
  };
};

export {useInstanceExecutionHistory, useIsInstanceExecutionHistoryAvailable};
