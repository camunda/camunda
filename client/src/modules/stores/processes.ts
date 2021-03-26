/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {makeAutoObservable} from 'mobx';
import {fetchGroupedProcesses} from 'modules/api/instances';
import {logger} from 'modules/logger';

type ProcessVersion = {
  bpmnProcessId: string;
  id: string;
  name: string;
  version: number;
};

type Process = {
  bpmnProcessId: string;
  name: string;
  processes: ProcessVersion[];
};

type State = {
  processes: Process[];
  status: 'initial' | 'fetching' | 'fetched' | 'fetch-error';
};

const INITIAL_STATE: State = {
  processes: [],
  status: 'initial',
};

class Processes {
  state: State = INITIAL_STATE;

  constructor() {
    makeAutoObservable(this, {
      fetchProcesses: false,
    });
  }

  fetchProcesses = async () => {
    this.startFetching();

    try {
      const response = await fetchGroupedProcesses();

      if (response.ok) {
        this.handleFetchSuccess(await response.json());
      } else {
        this.handleFetchError();
      }
    } catch (error) {
      this.handleFetchError(error);
    }
  };

  startFetching = () => {
    this.state.status = 'fetching';
  };

  handleFetchError = (error?: Error) => {
    this.state.status = 'fetch-error';
    logger.error('Failed to fetch processes');

    if (error !== undefined) {
      logger.error(error);
    }
  };

  handleFetchSuccess = (processes: Process[]) => {
    this.state.processes = processes;
    this.state.status = 'fetched';
  };

  get processes() {
    return this.state.processes
      .map(({bpmnProcessId, name}) => ({
        value: bpmnProcessId,
        label: name ?? bpmnProcessId,
      }))
      .sort((process, nextProcess) => {
        const label = process.label.toUpperCase();
        const nextLabel = nextProcess.label.toUpperCase();

        if (label < nextLabel) {
          return -1;
        }
        if (label > nextLabel) {
          return 1;
        }

        return 0;
      });
  }

  get versionsByProcess(): {
    [bpmnProcessId: string]: ProcessVersion[];
  } {
    return this.state.processes.reduce<{
      [bpmnProcessId: string]: ProcessVersion[];
    }>(
      (accumulator, {bpmnProcessId, processes}) => ({
        ...accumulator,
        [bpmnProcessId]: processes
          .slice()
          .sort(
            (process, nextProcess) => process.version - nextProcess.version
          ),
      }),
      {}
    );
  }

  reset = () => {
    this.state = INITIAL_STATE;
  };
}

const processesStore = new Processes();

export {processesStore};
