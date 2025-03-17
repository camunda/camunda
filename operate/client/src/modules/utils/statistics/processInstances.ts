/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ProcessInstancesStatisticsDto} from 'modules/api/v2/processInstances/fetchProcessInstancesStatistics';

function getInstancesCount(
  data: ProcessInstancesStatisticsDto[],
  flowNodeId: string | undefined,
) {
  const flowNodeStatistics = data.find(
    (statistics) => statistics.flowNodeId === flowNodeId,
  );

  if (flowNodeStatistics === undefined) {
    return 0;
  }

  return flowNodeStatistics.active + flowNodeStatistics.incidents;
}

export {getInstancesCount};
