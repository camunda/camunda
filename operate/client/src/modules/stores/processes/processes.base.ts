/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {makeObservable, action, observable, computed, override} from 'mobx';

import {
  fetchGroupedProcesses,
  ProcessDto,
  ProcessVersionDto,
} from 'modules/api/processes/fetchGroupedProcesses';
import {getProcessInstanceFilters} from 'modules/utils/filter/getProcessInstanceFilters';
import {getSearchString} from 'modules/utils/getSearchString';
import {NetworkReconnectionHandler} from '../networkReconnectionHandler';
import {sortOptions} from 'modules/utils/sortOptions';
import {DEFAULT_TENANT, PERMISSIONS} from 'modules/constants';
import {generateProcessKey} from 'modules/utils/generateProcessKey';
import {Location} from 'react-router-dom';

type Process = ProcessDto & {key: string};
type State = {
  processes: Process[];
  status: 'initial' | 'first-fetch' | 'fetching' | 'fetched' | 'fetch-error';
};

const INITIAL_STATE: State = {
  processes: [],
  status: 'initial',
};

class ProcessesBase extends NetworkReconnectionHandler {
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
      versionsByProcessAndTenant: computed,
      reset: override,
      isInitialLoadComplete: computed,
      filteredProcesses: computed,
    });
  }

  fetchProcesses = this.retryOnConnectionLost(async (tenantId?: string) => {
    this.startFetching();

    const {process, tenant: tenantFromURL} =
      getProcessInstanceFilters(getSearchString());

    const tenant = tenantId ?? tenantFromURL;

    const response = await fetchGroupedProcesses(
      tenant === 'all' ? undefined : tenant,
    );

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
    if (this.state.status === 'initial') {
      this.state.status = 'first-fetch';
    } else {
      this.state.status = 'fetching';
    }
  };

  handleFetchError = () => {
    this.state.status = 'fetch-error';
  };

  get isInitialLoadComplete() {
    if (['initial', 'first-fetch'].includes(this.state.status)) {
      return false;
    }

    return this.state.status !== 'fetching' || this.retryCount === 0;
  }

  handleFetchSuccess = (processes: ProcessDto[]) => {
    this.state.processes = processes.map((process) => {
      return {
        key: generateProcessKey(process.bpmnProcessId, process.tenantId),
        ...process,
      };
    });
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
    return this.filteredProcesses
      .map(({key, tenantId, bpmnProcessId, name}) => ({
        id: key,
        label: name ?? bpmnProcessId,
        bpmnProcessId,
        tenantId,
      }))
      .sort(sortOptions);
  }

  get filteredProcesses() {
    return this.state.processes;
  }

  get versionsByProcessAndTenant(): {
    [key: string]: ProcessVersionDto[];
  } {
    return this.filteredProcesses.reduce<{
      [key: string]: ProcessVersionDto[];
    }>(
      (versionsByProcessAndTenant, {key, processes}) => ({
        ...versionsByProcessAndTenant,
        [key]: processes
          .slice()
          .sort(
            (process, nextProcess) => process.version - nextProcess.version,
          ),
      }),
      {},
    );
  }

  getProcessId = ({
    process,
    tenant,
    version,
  }: {
    process?: string;
    tenant?: string;
    version?: string;
  } = {}) => {
    if (process === undefined || version === undefined || version === 'all') {
      return undefined;
    }

    const processVersions =
      this.versionsByProcessAndTenant[generateProcessKey(process, tenant)] ??
      [];

    return processVersions.find(
      (processVersion) => processVersion.version === parseInt(version),
    )?.id;
  };

  getProcessIdByLocation = (location: Location) => {
    const {process, version, tenant} = getProcessInstanceFilters(
      location.search,
    );

    return this.getProcessId({process, tenant, version});
  };

  resetRetryProcessesFetch = () => {
    if (this.retryProcessesFetchTimeout !== null) {
      clearTimeout(this.retryProcessesFetchTimeout);
    }

    this.retryCount = 0;
  };

  getProcess = ({
    bpmnProcessId,
    tenantId,
  }: {
    bpmnProcessId?: string;
    tenantId?: string;
  }) => {
    if (bpmnProcessId === undefined) {
      return undefined;
    }

    return this.state.processes.find(
      (process) =>
        process.bpmnProcessId === bpmnProcessId &&
        process.tenantId === (tenantId ?? DEFAULT_TENANT),
    );
  };

  getPermissions = (processId?: string, tenantId?: string) => {
    if (!window.clientConfig?.resourcePermissionsEnabled) {
      return PERMISSIONS;
    }

    if (processId === undefined) {
      return [];
    }

    return this.getProcess({bpmnProcessId: processId, tenantId})?.permissions;
  };

  // This can't be a computed value, because it depends on window.location
  getSelectedProcessDetails = () => {
    const {process, tenant, version} =
      getProcessInstanceFilters(getSearchString());

    const selectedProcess = this.getProcess({
      bpmnProcessId: process,
      tenantId: tenant,
    });

    const bpmnProcessId = selectedProcess?.bpmnProcessId;
    const processName = selectedProcess?.name ?? bpmnProcessId ?? 'Process';

    const versionTag = selectedProcess?.processes.find((process) => {
      return process.version.toString() === version;
    })?.versionTag;

    return {
      key: selectedProcess?.key,
      bpmnProcessId,
      processName,
      version,
      versionTag,
    };
  };

  reset() {
    super.reset();
    this.state = INITIAL_STATE;
    this.resetRetryProcessesFetch();
  }
}

export {ProcessesBase};
export type {Process};
