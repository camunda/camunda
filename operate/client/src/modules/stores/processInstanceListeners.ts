/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {makeAutoObservable} from 'mobx';
import {fetchProcessInstanceListeners} from 'modules/api/processInstances/fetchProcessInstanceListeners';

type State = {
  listeners: ListenerEntity[];
  status: 'initial' | 'fetched' | 'error';
};
const DEFAULT_STATE: State = {
  listeners: [],
  status: 'initial',
};

class ProcessInstanceListeners {
  state: State = {...DEFAULT_STATE};

  constructor() {
    makeAutoObservable(this);
  }

  fetchListeners = async (
    processInstanceId: ProcessInstanceEntity['id'],
    flowNodeId: string,
  ) => {
    const response = await fetchProcessInstanceListeners({
      processInstanceId,
      payload: {
        pageSize: 50,
        flowNodeId,
      },
    });

    if (response.isSuccess) {
      this.handleFetchSuccess(response.data);
    } else {
      this.handleFetchFailure();
    }
  };

  handleFetchSuccess = (listeners: ListenerEntity[]) => {
    this.state.listeners = listeners;
    this.state.status = 'fetched';
  };

  handleFetchFailure = () => {
    this.state.status = 'error';
  };

  reset = () => {
    this.state = {...DEFAULT_STATE};
  };
}

export const processInstanceListenersStore = new ProcessInstanceListeners();
