/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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

    return {key: selectedProcess?.key, bpmnProcessId, processName, version};
  };

  reset() {
    super.reset();
    this.state = INITIAL_STATE;
    this.resetRetryProcessesFetch();
  }
}

export {ProcessesBase};
export type {Process};
