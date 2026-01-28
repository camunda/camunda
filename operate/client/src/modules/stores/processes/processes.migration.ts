/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  action,
  autorun,
  computed,
  makeObservable,
  observable,
  override,
  type IReactionDisposer,
} from 'mobx';
import {ProcessesBase, type Process} from './processes.base';

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
  versionSelectDisposer: null | IReactionDisposer = null;

  constructor() {
    super();
    makeObservable(this, {
      migrationState: observable,
      latestProcessVersion: computed,
      targetProcessVersions: computed,
      selectedTargetProcessId: computed,
      filteredProcesses: override,
      setSelectedTargetVersion: action,
      setSelectedTargetProcess: action,
      clearSelectedTarget: action,
      reset: override,
    });
  }

  init = () => {
    this.versionSelectDisposer = autorun(() => {
      const {key} = this.getSelectedProcessDetails();

      if (key !== undefined) {
        // pre-select the same process
        this.setSelectedTargetProcess(key);
      }

      if (this.latestProcessVersion !== undefined) {
        // preselect the latest available version
        this.setSelectedTargetVersion(this.latestProcessVersion);
      }
    });
  };

  getSourceProcessKey = () => {
    return this.getSelectedProcessDetails().key;
  };

  /**
   * Gets the latest available version of the source process.
   * Returns undefined if no newer version is available.
   */
  get latestProcessVersion() {
    const sourceProcess = this.getSelectedProcessDetails();

    if (sourceProcess.key) {
      const processVersions =
        this.versionsByProcessAndTenant[sourceProcess.key];
      if (processVersions) {
        // sort processes by version descending
        const sortedProcessVersions = processVersions.sort(
          (a, b) => b.version - a.version,
        );
        const latestVersion = sortedProcessVersions[0]?.version;
        // return the latest version (only if a newer version is available)
        if (
          (latestVersion !== undefined &&
            latestVersion > Number(sourceProcess.version)) ??
          latestVersion
        ) {
          return latestVersion;
        }
      }
    }
    return undefined;
  }

  get filteredProcesses() {
    const {key: sourceProcessKey, version: sourceProcessVersion} =
      this.getSelectedProcessDetails();

    return this.state.processes.reduce<Process[]>(
      (selectableTargetProcesses, process) => {
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

  clearSelectedTarget = () => {
    this.migrationState.selectedTargetProcess = null;
    this.migrationState.selectedTargetVersion = null;
  };

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
    this.versionSelectDisposer?.();
    this.migrationState = {...DEFAULT_MIGRATION_STATE};
  }
}

const processesStore = new Processes();

export {processesStore};
