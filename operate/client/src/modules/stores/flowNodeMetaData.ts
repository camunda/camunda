/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  action,
  computed,
  IReactionDisposer,
  makeObservable,
  observable,
  override,
} from 'mobx';
import {MetaDataDto} from 'modules/api/processInstances/fetchFlowNodeMetaData';
import {NetworkReconnectionHandler} from './networkReconnectionHandler';

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
      isSelectedInstanceRunning: computed,
      reset: override,
      startFetching: action,
      handleSuccess: action,
      handleError: action,
    });
  }

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
