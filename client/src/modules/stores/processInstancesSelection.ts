/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {makeAutoObservable, autorun, IReactionDisposer, Lambda} from 'mobx';
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
      this.state.selectedProcessInstanceIds.filter((prevId) => prevId !== id)
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
    if (this.state.selectionMode === 'ALL') {
      this.setMode('INCLUDE');
      this.setAllChecked(false);
    } else {
      this.setMode('ALL');
      this.setAllChecked(true);
      this.setselectedProcessInstanceIds([]);
    }
  };

  selectAllProcessInstancesCarbon = () => {
    if (
      this.state.selectionMode === 'INCLUDE' &&
      this.getSelectedProcessInstanceCount() === 0
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
    } else {
      this.addToselectedProcessInstanceIds(id);
    }
  };

  selectProcessInstanceCarbon = (id: string) => {
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

  getSelectedProcessInstanceCount = () => {
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
  };

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
