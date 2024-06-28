/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {makeAutoObservable} from 'mobx';
import {
  fetchProcessInstanceListeners,
  ListenerPayload,
} from 'modules/api/processInstances/fetchProcessInstanceListeners';

type FetchType = 'initial' | 'prev' | 'next';

type State = {
  listeners: ListenerEntity[];
  page: number;
  status:
    | 'initial'
    | 'first-fetch'
    | 'fetching'
    | 'fetching-next'
    | 'fetching-prev'
    | 'fetched'
    | 'error';
};

const DEFAULT_STATE: State = {
  listeners: [],
  page: 1,
  status: 'initial',
};

const DEFAULT_PAYLOAD: ListenerPayload = {flowNodeId: '', pageSize: 20};

class ProcessInstanceListeners {
  state: State = {...DEFAULT_STATE};

  constructor() {
    makeAutoObservable(this);
  }

  startFetching = () => {
    if (this.state.status === 'initial') {
      this.state.status = 'first-fetch';
    } else {
      this.state.status = 'fetching';
    }
  };

  handleFetchSuccess = (listeners: ListenerEntity[]) => {
    this.state.listeners = listeners;
    this.state.status = 'fetched';
  };

  handleFetchFailure = () => {
    this.state.status = 'error';
  };

  fetchListeners = async (
    fetchType: FetchType,
    processInstanceId: ProcessInstanceEntity['id'],
    payload: ListenerPayload = DEFAULT_PAYLOAD,
  ) => {
    if (fetchType === 'initial') {
      this.startFetching();
    }

    const response = await fetchProcessInstanceListeners({
      processInstanceId,
      payload,
    });

    if (response.isSuccess) {
      this.handleFetchSuccess(response.data);
    } else {
      this.handleFetchFailure();
    }
  };

  reset = () => {
    this.state = {...DEFAULT_STATE};
  };
}

export const processInstanceListenersStore = new ProcessInstanceListeners();
