/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {MODIFICATIONS} from 'modules/bpmn-js/badgePositions';
import {ProcessStatistics as ProcessStatisticsBase} from './processStatistics.base';

class ProcessStatisticsBatchModification extends ProcessStatisticsBase {
  getInstancesCount(flowNodeId?: string) {
    const flowNodeStatistics = this.statistics.find(
      (statistics) => statistics.activityId === flowNodeId,
    );

    if (flowNodeStatistics === undefined) {
      return 0;
    }

    return flowNodeStatistics.active + flowNodeStatistics.incidents;
  }

  getOverlaysData = ({
    sourceFlowNodeId,
    targetFlowNodeId,
  }: {
    sourceFlowNodeId?: string;
    targetFlowNodeId?: string;
  }) => {
    if (
      targetFlowNodeId === undefined ||
      sourceFlowNodeId === undefined ||
      this.state.status !== 'fetched' ||
      this.statistics.length === 0
    ) {
      return [];
    }

    return [
      {
        payload: {
          cancelledTokenCount:
            processStatisticsBatchModificationStore.getInstancesCount(
              sourceFlowNodeId,
            ),
        },
        type: 'batchModificationsBadge',
        flowNodeId: sourceFlowNodeId,
        position: MODIFICATIONS,
      },
      {
        payload: {
          newTokenCount:
            processStatisticsBatchModificationStore.getInstancesCount(
              sourceFlowNodeId,
            ),
        },
        type: 'batchModificationsBadge',
        flowNodeId: targetFlowNodeId,
        position: MODIFICATIONS,
      },
    ];
  };
}

const processStatisticsBatchModificationStore =
  new ProcessStatisticsBatchModification();

export {processStatisticsBatchModificationStore};
