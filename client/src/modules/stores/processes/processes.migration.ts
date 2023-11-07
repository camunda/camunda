/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {action, computed, makeObservable, observable, override} from 'mobx';
import {ProcessesBase, Process} from './processes.base';

type MigrationState = {
  selectedTargetProcess: Omit<Process, 'permissions' | 'processes'> | null;
  selectedTargetVersion: number | null;
};

const DEFAULT_MIGRATION_STATE: MigrationState = {
  selectedTargetProcess: null,
  selectedTargetVersion: null,
};

class Processes extends ProcessesBase {
  migrationState: MigrationState = DEFAULT_MIGRATION_STATE;

  constructor() {
    super();
    makeObservable(this, {
      migrationState: observable,
      targetProcessVersions: computed,
      selectedTargetProcessId: computed,
      filteredProcesses: override,
      setSelectedTargetVersion: action,
      setSelectedTargetProcess: action,
      reset: override,
    });
  }

  get filteredProcesses() {
    const {key: sourceProcessKey, version: sourceProcessVersion} =
      this.getSelectedProcessDetails();

    return this.state.processes.reduce<Process[]>(
      (selectableTargetProcesses, process) => {
        if (
          window?.clientConfig?.resourcePermissionsEnabled &&
          !process.permissions?.includes('UPDATE_PROCESS_INSTANCE')
        ) {
          return selectableTargetProcesses;
        }

        if (process.key === sourceProcessKey) {
          const filteredVersions = process.processes.filter(
            ({version}) => version.toString() !== sourceProcessVersion,
          );

          return filteredVersions.length === 0
            ? selectableTargetProcesses
            : [
                ...selectableTargetProcesses,
                {...process, processes: filteredVersions},
              ];
        }

        return [...selectableTargetProcesses, process];
      },
      [],
    );
  }

  setSelectedTargetProcess = (selectedTargetProcessKey: string) => {
    const process = this.processes.find(
      ({id}) => id === selectedTargetProcessKey,
    );

    if (process !== undefined) {
      this.migrationState.selectedTargetProcess = {
        key: process.id,
        bpmnProcessId: process.bpmnProcessId,
        name: process.label,
        tenantId: process.tenantId,
      };
    }
  };

  setSelectedTargetVersion = (selectedTargetVersion: number | null) => {
    this.migrationState.selectedTargetVersion = selectedTargetVersion;
  };

  get targetProcessVersions() {
    if (this.migrationState.selectedTargetProcess?.key === undefined) {
      return [];
    }

    const versions =
      this.versionsByProcessAndTenant[
        this.migrationState.selectedTargetProcess.key
      ] ?? [];
    return versions.map(({version}) => version);
  }

  get selectedTargetProcessId() {
    if (this.migrationState.selectedTargetVersion === undefined) {
      return undefined;
    }

    return this.getProcessId({
      process: this.migrationState.selectedTargetProcess?.bpmnProcessId,
      tenant: this.migrationState.selectedTargetProcess?.tenantId,
      version: this.migrationState.selectedTargetVersion?.toString(),
    });
  }

  reset() {
    super.reset();
    this.migrationState = {...DEFAULT_MIGRATION_STATE};
  }
}

const processesStore = new Processes();

export {processesStore};
