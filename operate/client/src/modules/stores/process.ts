/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {makeAutoObservable} from 'mobx';
import {fetchProcess, ProcessDto} from 'modules/api/processes/fetchProcess';
import {logger} from 'modules/logger';

type State = {
  process: ProcessDto | null;
  status: 'initial' | 'fetching' | 'fetched' | 'error';
};

const DEFAULT_STATE: State = {
  process: null,
  status: 'initial',
};

class Process {
  state: State = {...DEFAULT_STATE};

  constructor() {
    makeAutoObservable(this);
  }

  fetchProcess = async (id: string) => {
    this.state.status = 'fetching';
    const response = await fetchProcess(id);

    if (response.isSuccess) {
      this.handleFetchSuccess(response.data);
    } else {
      this.handleFetchFailure();
    }
  };

  handleFetchSuccess = (process: ProcessDto) => {
    this.state.process = process;
    this.state.status = 'fetched';
  };

  handleFetchFailure = () => {
    this.state.status = 'error';
    logger.error('Failed to fetch process');
  };

  reset = () => {
    this.state = {...DEFAULT_STATE};
  };
}

export const processStore = new Process();
