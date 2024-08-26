/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {computed, makeObservable} from 'mobx';
import {ProcessesBase} from './processes.base';
import {ProcessVersionDto} from 'modules/api/processes/fetchGroupedProcesses';

class Processes extends ProcessesBase {
  constructor() {
    super();
    makeObservable(this, {
      processVersions: computed,
      hasVersionTags: computed,
    });
  }

  /**
   * flat list of all process versions
   */
  get processVersions() {
    return this.filteredProcesses.reduce<ProcessVersionDto[]>(
      (processVersions, process) => {
        return [
          ...processVersions,
          ...process.processes.map((processDefinition) => processDefinition),
        ];
      },
      [],
    );
  }

  /**
   * Returns true if there are is at least one process version with a version tag.
   */
  get hasVersionTags() {
    return this.processVersions.some(({versionTag}) => versionTag !== null);
  }

  getVersionTag(processId: string) {
    return this.processVersions.find(({id}) => {
      return id === processId;
    })?.versionTag;
  }
}

const processesStore = new Processes();

export {processesStore};
