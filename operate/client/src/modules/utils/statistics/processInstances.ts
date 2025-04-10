/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelection';
import {ProcessDefinitionStatistic} from '@vzeta/camunda-api-zod-schemas/operate';

function getInstancesCount(
  data: ProcessDefinitionStatistic[],
  flowNodeId?: string,
) {
  const flowNodeStatistics = data.find(
    (statistics) => statistics.flowNodeId === flowNodeId,
  );

  if (flowNodeStatistics === undefined) {
    return 0;
  }

  return flowNodeStatistics.active + flowNodeStatistics.incidents;
}

const getProcessInstanceKey = () => {
  return processInstancesSelectionStore.checkedProcessInstanceIds.length > 0
    ? {
        $in: processInstancesSelectionStore.checkedProcessInstanceIds,
      }
    : undefined;
};

export {getInstancesCount, getProcessInstanceKey};
