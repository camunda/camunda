/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {makeObservable, action, observable, computed, override} from 'mobx';

import {
  fetchGroupedProcesses,
  ProcessDto,
  ProcessVersionDto,
} from 'modules/api/processes/fetchGroupedProcesses';
import {getProcessInstanceFilters} from 'modules/utils/filter';
import {getSearchString} from 'modules/utils/getSearchString';
import {NetworkReconnectionHandler} from './networkReconnectionHandler';
import {sortOptions} from 'modules/utils/sortOptions';
import {PERMISSIONS} from 'modules/constants';

type State = {
  processes: ProcessDto[];
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

    const response = await fetchGroupedProcesses();

    if (response.isSuccess) {
      const processes = response.data;

      if (
        process !== undefined &&
        processes.filter((item) => item.bpmnProcessId === process).length === 0
      ) {
        this.handleRefetch(processes);
      } else {
        this.resetRetryProcessesFetch();
        this.handleFetchSuccess(processes);
      }
    } else {
      this.handleFetchError();
    }
  });

  startFetching = () => {
    this.state.status = 'fetching';
  };

  handleFetchError = () => {
    this.state.status = 'fetch-error';
  };

  handleFetchSuccess = (processes: ProcessDto[]) => {
    this.state.processes = processes;
    this.state.status = 'fetched';
  };

  handleRefetch = (processes: ProcessDto[]) => {
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
      .sort(sortOptions);
  }

  get versionsByProcess(): {
    [bpmnProcessId: string]: ProcessVersionDto[];
  } {
    return this.state.processes.reduce<{
      [bpmnProcessId: string]: ProcessVersionDto[];
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

  getProcessId = (process?: string, version?: string) => {
    if (process === undefined || version === undefined || version === 'all') {
      return undefined;
    }

    const processVersions = this.versionsByProcess[process] ?? [];

    return processVersions.find(
      (processVersion) => processVersion.version === parseInt(version)
    )?.id;
  };

  resetRetryProcessesFetch = () => {
    if (this.retryProcessesFetchTimeout !== null) {
      clearTimeout(this.retryProcessesFetchTimeout);
    }

    this.retryCount = 0;
  };

  getPermissions = (processId?: string) => {
    if (!window.clientConfig?.resourcePermissionsEnabled) {
      return PERMISSIONS;
    }

    if (processId === undefined) {
      return [];
    }

    return this.state.processes.find(
      (process) => process.bpmnProcessId === processId
    )?.permissions;
  };

  reset() {
    super.reset();
    this.state = INITIAL_STATE;
    this.resetRetryProcessesFetch();
  }
}

const processesStore = new Processes();

export {processesStore};
