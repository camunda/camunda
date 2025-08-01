/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {fetchFlowNodeMetaData} from 'modules/api/processInstances/fetchFlowNodeMetaData';
import type {FlowNodeInstance} from 'modules/stores/flowNodeInstance';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
import {modificationsStore} from 'modules/stores/modifications';
import {formatDate} from './date';
import type {ProcessDefinitionStatistic} from '@vzeta/camunda-api-zod-schemas/8.8';
import {reaction} from 'mobx';
import {
  flowNodeSelectionStore,
  type Selection,
} from 'modules/stores/flowNodeSelection';

const init = (
  processInstanceId: string,
  statistics: ProcessDefinitionStatistic[],
) => {
  flowNodeMetaDataStore.selectionDisposer = reaction(
    () => flowNodeSelectionStore.state.selection,
    (selection: Selection | null) => {
      flowNodeMetaDataStore.setMetaData(null);
      if (selection !== null) {
        fetchMetaData(statistics, processInstanceId, selection);
      }
    },
  );
};

const fetchMetaData = flowNodeMetaDataStore.retryOnConnectionLost(
  async (
    statistics: ProcessDefinitionStatistic[],
    processInstanceId: string,
    {
      flowNodeId,
      flowNodeInstanceId,
      flowNodeType,
      isPlaceholder,
    }: {
      flowNodeId?: string;
      flowNodeInstanceId?: FlowNodeInstance['id'];
      flowNodeType?: string;
      isPlaceholder?: boolean;
    },
  ) => {
    if (
      isPlaceholder ||
      processInstanceId === undefined ||
      flowNodeId === undefined ||
      (modificationsStore.isModificationModeEnabled &&
        !statistics.some(({elementId: id}) => id === flowNodeId))
    ) {
      return;
    }

    flowNodeMetaDataStore.startFetching();

    const response = await fetchFlowNodeMetaData({
      flowNodeId,
      processInstanceId,
      flowNodeInstanceId,
      flowNodeType,
    });

    if (response.isSuccess) {
      const metaData = response.data;

      if (metaData.instanceMetadata !== null) {
        const {startDate, endDate, jobDeadline} = metaData.instanceMetadata;

        metaData.instanceMetadata = {
          ...metaData.instanceMetadata,
          startDate: formatDate(startDate, null)!,
          endDate: formatDate(endDate, null),
          jobDeadline: formatDate(jobDeadline, null),
        };
      }

      flowNodeMetaDataStore.handleSuccess(metaData);
    } else {
      flowNodeMetaDataStore.handleError();
    }
  },
);

export {init, fetchMetaData};
