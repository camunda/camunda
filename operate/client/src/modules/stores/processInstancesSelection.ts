/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  makeAutoObservable,
  autorun,
  type IReactionDisposer,
  type Lambda,
} from 'mobx';
import {processInstancesStore} from 'modules/stores/processInstances';

type Mode = 'INCLUDE' | 'EXCLUDE' | 'ALL';
type State = {
  selectedProcessInstanceIds: string[];
  isAllChecked: boolean;
  selectionMode: Mode;
};

const DEFAULT_STATE: State = {
  selectedProcessInstanceIds: [],
  isAllChecked: false,
  selectionMode: 'INCLUDE',
};

class ProcessInstancesSelection {
  state: State = {...DEFAULT_STATE};
  autorunDisposer: null | IReactionDisposer = null;
  observeDisposer: null | Lambda = null;

  constructor() {
    makeAutoObservable(this);
  }

  init() {
    const {selectionMode, selectedProcessInstanceIds} = this.state;
    const {filteredProcessInstancesCount} = processInstancesStore.state;

    this.autorunDisposer = autorun(() => {
      if (
        (selectionMode === 'EXCLUDE' &&
          selectedProcessInstanceIds.length === 0) ||
        (selectionMode === 'INCLUDE' &&
          selectedProcessInstanceIds.length === filteredProcessInstancesCount &&
          filteredProcessInstancesCount !== 0)
      ) {
        this.setMode('ALL');
        this.setAllChecked(true);
        this.setselectedProcessInstanceIds([]);
      }
    });
  }

  setMode(mode: Mode) {
    this.state.selectionMode = mode;
  }

  setAllChecked(isAllChecked: boolean) {
    this.state.isAllChecked = isAllChecked;
  }

  setselectedProcessInstanceIds(ids: string[]) {
    this.state.selectedProcessInstanceIds = ids;
  }

  addToselectedProcessInstanceIds = (id: string) => {
    this.setselectedProcessInstanceIds([
      ...this.state.selectedProcessInstanceIds,
      id,
    ]);
  };

  removeFromselectedProcessInstanceIds = (id: string) => {
    this.setselectedProcessInstanceIds(
      this.state.selectedProcessInstanceIds.filter((prevId) => prevId !== id),
    );
  };

  isProcessInstanceChecked = (id: string) => {
    const {selectionMode, selectedProcessInstanceIds} = this.state;

    switch (selectionMode) {
      case 'INCLUDE':
        return selectedProcessInstanceIds.indexOf(id) >= 0;
      case 'EXCLUDE':
        return selectedProcessInstanceIds.indexOf(id) < 0;
      default:
        return selectionMode === 'ALL';
    }
  };

  selectAllProcessInstances = () => {
    if (
      this.state.selectionMode === 'INCLUDE' &&
      this.selectedProcessInstanceCount === 0
    ) {
      this.setMode('ALL');
      this.setAllChecked(true);
    } else {
      this.setMode('INCLUDE');
      this.setAllChecked(false);
      this.setselectedProcessInstanceIds([]);
    }
  };

  selectProcessInstance = (id: string) => {
    const {selectionMode, selectedProcessInstanceIds} = this.state;

    if (selectionMode === 'ALL') {
      this.setMode('EXCLUDE');
      this.setAllChecked(false);
    }

    if (selectedProcessInstanceIds.indexOf(id) >= 0) {
      this.removeFromselectedProcessInstanceIds(id);

      if (
        selectionMode === 'EXCLUDE' &&
        this.state.selectedProcessInstanceIds.length === 0
      ) {
        this.setMode('ALL');
        this.setAllChecked(true);
      }
    } else {
      this.addToselectedProcessInstanceIds(id);
    }
  };

  get selectedProcessInstanceCount() {
    const {selectionMode, selectedProcessInstanceIds} = this.state;
    const {filteredProcessInstancesCount} = processInstancesStore.state;

    switch (selectionMode) {
      case 'INCLUDE':
        return selectedProcessInstanceIds.length;
      case 'EXCLUDE':
        return (
          (filteredProcessInstancesCount ?? 0) -
          selectedProcessInstanceIds.length
        );
      default:
        return filteredProcessInstancesCount;
    }
  }

  get hasSelectedRunningInstances() {
    const {
      selectedProcessInstanceIds,
      state: {isAllChecked, selectionMode},
    } = this;

    return (
      isAllChecked ||
      selectionMode === 'EXCLUDE' ||
      processInstancesStore.state.processInstances.some((processInstance) => {
        return (
          selectedProcessInstanceIds.includes(processInstance.id) &&
          ['ACTIVE', 'INCIDENT'].includes(processInstance.state)
        );
      })
    );
  }

  get checkedRunningProcessInstanceIds() {
    const {selectionMode, selectedProcessInstanceIds} = this.state;
    const runningInstances =
      processInstancesStore.state.processInstances.filter((instance) =>
        ['ACTIVE', 'INCIDENT'].includes(instance.state),
      );

    if (selectionMode === 'INCLUDE') {
      return selectedProcessInstanceIds.filter((id) =>
        runningInstances.some((instance) => instance.id === id),
      );
    }

    const allRunningInstanceIds = runningInstances.map(
      (instance) => instance.id,
    );

    return allRunningInstanceIds.filter(
      (id) => !selectedProcessInstanceIds.includes(id),
    );
  }

  get checkedProcessInstanceIds() {
    const {selectionMode, selectedProcessInstanceIds} = this.state;

    if (selectionMode === 'INCLUDE') {
      return selectedProcessInstanceIds;
    }

    const allProcessInstanceIds =
      processInstancesStore.state.processInstances.map(
        (processInstance) => processInstance.id,
      );

    return allProcessInstanceIds.filter(
      (id) => !selectedProcessInstanceIds.includes(id),
    );
  }

  get selectedProcessInstanceIds() {
    const {selectionMode, selectedProcessInstanceIds} = this.state;

    return selectionMode === 'INCLUDE' ? selectedProcessInstanceIds : [];
  }

  get excludedProcessInstanceIds() {
    const {selectionMode, selectedProcessInstanceIds} = this.state;

    return selectionMode === 'EXCLUDE' ? selectedProcessInstanceIds : [];
  }

  resetState = () => {
    this.state = {...DEFAULT_STATE};
  };

  reset = () => {
    this.resetState();
    this.autorunDisposer?.();
    this.observeDisposer?.();
  };
}

export const processInstancesSelectionStore = new ProcessInstancesSelection();
