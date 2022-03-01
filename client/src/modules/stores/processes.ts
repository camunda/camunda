/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {makeObservable, action, observable, computed, override} from 'mobx';
import {fetchGroupedProcesses} from 'modules/api/instances';
import {getProcessInstanceFilters} from 'modules/utils/filter';
import {getSearchString} from 'modules/utils/getSearchString';
import {logger} from 'modules/logger';
import {NetworkReconnectionHandler} from './networkReconnectionHandler';

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

class Processes extends NetworkReconnectionHandler {
  state: State = INITIAL_STATE;
  retryCount: number = 0;
  retryProcessesFetchTimeout: NodeJS.Timeout | null = null;

  constructor() {
    super();
    makeObservable(this, {
      state: observable,
      startFetching: action,
      handleFetchError: action,
      handleFetchSuccess: action,
      processes: computed,
      versionsByProcess: computed,
      reset: override,
    });
  }

  fetchProcesses = this.retryOnConnectionLost(async () => {
    this.startFetching();

    const {process} = getProcessInstanceFilters(getSearchString());

    try {
      const response = await fetchGroupedProcesses();

      if (response.ok) {
        const processes: Process[] = await response.json();

        if (
          process !== undefined &&
          processes.filter((item) => item.bpmnProcessId === process).length ===
            0
        ) {
          this.handleRefetch(processes);
        } else {
          this.resetRetryProcessesFetch();
          this.handleFetchSuccess(processes);
        }
      } else {
        this.handleFetchError();
      }
    } catch (error) {
      this.handleFetchError(error);
    }
  });

  startFetching = () => {
    this.state.status = 'fetching';
  };

  handleFetchError = (error?: unknown) => {
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

  handleRefetch = (processes: Process[]) => {
    if (this.retryCount < 3) {
      this.retryCount += 1;

      this.retryProcessesFetchTimeout = setTimeout(() => {
        this.fetchProcesses();
      }, 5000);
    } else {
      this.resetRetryProcessesFetch();
      this.handleFetchSuccess(processes);
    }
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

  resetRetryProcessesFetch = () => {
    if (this.retryProcessesFetchTimeout !== null) {
      clearTimeout(this.retryProcessesFetchTimeout);
    }

    this.retryCount = 0;
  };

  reset() {
    super.reset();
    this.state = INITIAL_STATE;
    this.resetRetryProcessesFetch();
  }
}

const processesStore = new Processes();

export {processesStore};
