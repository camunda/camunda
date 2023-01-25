/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  action,
  computed,
  IReactionDisposer,
  makeObservable,
  observable,
  reaction,
  override,
} from 'mobx';
import {
  fetchFlowNodeMetaData,
  MetaDataDto,
} from 'modules/api/processInstances/fetchFlowNodeMetaData';
import {FlowNodeInstance} from './flowNodeInstance';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {flowNodeSelectionStore, Selection} from './flowNodeSelection';
import {NetworkReconnectionHandler} from './networkReconnectionHandler';
import {formatDate} from 'modules/utils/date';
import {modificationsStore} from './modifications';
import {processInstanceDetailsStatisticsStore} from './processInstanceDetailsStatistics';

type State = {
  metaData: MetaDataDto | null;
  status: 'initial' | 'fetching' | 'fetched' | 'error';
};

const DEFAULT_STATE: State = {
  metaData: null,
  status: 'initial',
};

class FlowNodeMetaData extends NetworkReconnectionHandler {
  state: State = {...DEFAULT_STATE};
  selectionDisposer: null | IReactionDisposer = null;

  constructor() {
    super();
    makeObservable(this, {
      state: observable,
      setMetaData: action,
      hasMultipleInstances: computed,
      isSelectedInstanceRunning: computed,
      reset: override,
      startFetching: action,
      handleSuccess: action,
      handleError: action,
    });
  }

  init = () => {
    this.selectionDisposer = reaction(
      () => flowNodeSelectionStore.state.selection,
      (selection: Selection | null) => {
        this.setMetaData(null);
        if (selection !== null) {
          this.fetchMetaData(selection);
        }
      }
    );
  };
  startFetching = () => {
    this.state.status = 'fetching';
  };

  handleSuccess = (metaData: MetaDataDto | null) => {
    this.state.status = 'fetched';
    this.setMetaData(metaData);
  };

  handleError = () => {
    this.state.status = 'error';
  };

  setMetaData = (metaData: MetaDataDto | null) => {
    this.state.metaData = metaData;
  };

  get hasMultipleInstances() {
    const {metaData} = this.state;

    if (
      flowNodeSelectionStore.state.selection?.flowNodeInstanceId !== undefined
    ) {
      return false;
    }

    if (!flowNodeSelectionStore.hasRunningOrFinishedTokens) {
      return flowNodeSelectionStore.newTokenCountForSelectedNode > 1;
    }

    return (
      (metaData?.instanceCount ?? 0) > 1 ||
      flowNodeSelectionStore.newTokenCountForSelectedNode > 0
    );
  }

  fetchMetaData = this.retryOnConnectionLost(
    async ({
      flowNodeId,
      flowNodeInstanceId,
      flowNodeType,
      isPlaceholder,
    }: {
      flowNodeId?: string;
      flowNodeInstanceId?: FlowNodeInstance['id'];
      flowNodeType?: string;
      isPlaceholder?: boolean;
    }) => {
      const processInstanceId =
        processInstanceDetailsStore.state.processInstance?.id;

      if (
        isPlaceholder ||
        processInstanceId === undefined ||
        flowNodeId === undefined ||
        (modificationsStore.isModificationModeEnabled &&
          !processInstanceDetailsStatisticsStore.state.statistics.some(
            ({activityId}) => activityId === flowNodeId
          ))
      ) {
        return;
      }

      this.startFetching();

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

        this.handleSuccess(metaData);
      } else {
        this.handleError();
      }
    }
  );

  get isSelectedInstanceRunning() {
    return this.state.metaData?.instanceMetadata?.endDate === null;
  }

  reset() {
    super.reset();
    this.state = {...DEFAULT_STATE};
    this.selectionDisposer?.();
  }
}

export const flowNodeMetaDataStore = new FlowNodeMetaData();
