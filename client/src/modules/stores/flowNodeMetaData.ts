/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {IReactionDisposer, makeAutoObservable, reaction} from 'mobx';
import {fetchFlowNodeMetaData} from 'modules/api/flowNodeMetaData';
import {FlowNodeInstance} from './flowNodeInstance';
import {currentInstanceStore} from 'modules/stores/currentInstance';
import {flowNodeSelectionStore, Selection} from './flowNodeSelection';
import {logger} from 'modules/logger';

type InstanceMetaData = {
  startDate: string;
  endDate: string | null;
  eventId: string;
  flowNodeId: string;
  flowNodeInstanceId: string;
  flowNodeType: string;
  incidentErrorMessage: string | null;
  incidentErrorType: string | null;
  jobCustomHeaders: {[key: string]: string} | null;
  jobDeadline: string | null;
  jobId: string | null;
  jobRetries: number | null;
  jobType: string | null;
  jobWorker: string | null;
};

type Breadcrumb = {flowNodeId: string; flowNodeType: string};

type MetaData = {
  breadcrumb: Breadcrumb[];
  flowNodeId: string | null;
  flowNodeInstanceId: string | null;
  flowNodeType: string;
  instanceCount: number | null;
  instanceMetadata: InstanceMetaData | null;
};

type State = {
  metaData: MetaData | null;
};

const DEFAULT_STATE: State = {
  metaData: null,
};

class FlowNodeMetaData {
  state: State = {...DEFAULT_STATE};
  selectionDisposer: null | IReactionDisposer = null;

  constructor() {
    makeAutoObservable(this, {init: false, fetchMetaData: false});
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

  setMetaData = (metaData: MetaData | null) => {
    this.state.metaData = metaData;
  };

  get hasMultipleInstances() {
    const {metaData} = this.state;
    return (
      metaData !== null &&
      metaData.instanceCount !== null &&
      metaData.instanceCount > 1
    );
  }

  handleFetchFailure = (error?: Error) => {
    logger.error('Failed to fetch flow node meta data');
    if (error !== undefined) {
      logger.error(error);
    }
  };

  fetchMetaData = async ({
    flowNodeId,
    flowNodeInstanceId,
    flowNodeType,
  }: {
    flowNodeId?: string;
    flowNodeInstanceId?: FlowNodeInstance['id'];
    flowNodeType?: string;
  }) => {
    const workflowInstanceId = currentInstanceStore.state.instance?.id;

    if (workflowInstanceId === undefined || flowNodeId === undefined) {
      return;
    }

    try {
      const response = await fetchFlowNodeMetaData({
        flowNodeId,
        workflowInstanceId,
        flowNodeInstanceId,
        flowNodeType,
      });

      if (response.ok) {
        this.setMetaData(await response.json());
      } else {
        this.handleFetchFailure();
      }
    } catch (error) {
      this.handleFetchFailure(error);
    }
  };

  get isSelectedInstanceRunning() {
    return this.state.metaData?.instanceMetadata?.endDate === null;
  }

  reset = () => {
    this.state = {...DEFAULT_STATE};
    this.selectionDisposer?.();
  };
}

export const flowNodeMetaDataStore = new FlowNodeMetaData();
export type {InstanceMetaData as InstanceMetaDataEntity};
export type {MetaData as MetaDataEntity};
