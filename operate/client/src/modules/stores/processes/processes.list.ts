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

  getVersionTag(processId: string) {
    return this.processVersions.find(({id}) => {
      return id === processId;
    })?.versionTag;
  }
}

const processesStore = new Processes();

export {processesStore};
