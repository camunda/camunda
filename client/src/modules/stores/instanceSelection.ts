/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {makeAutoObservable, autorun, IReactionDisposer, Lambda} from 'mobx';
import {instancesStore} from 'modules/stores/instances';
import {INSTANCE_SELECTION_MODE} from 'modules/constants';

type Mode = 'INCLUDE' | 'EXCLUDE' | 'ALL';
type State = {
  selectedInstanceIds: string[];
  isAllChecked: boolean;
  selectionMode: Mode;
};

const DEFAULT_STATE: State = {
  selectedInstanceIds: [],
  isAllChecked: false,
  selectionMode: INSTANCE_SELECTION_MODE.INCLUDE,
};

class InstanceSelection {
  state: State = {...DEFAULT_STATE};
  autorunDisposer: null | IReactionDisposer = null;
  observeDisposer: null | Lambda = null;

  constructor() {
    makeAutoObservable(this);
  }

  init() {
    const {selectionMode, selectedInstanceIds} = this.state;
    const {filteredInstancesCount} = instancesStore.state;

    this.autorunDisposer = autorun(() => {
      if (
        (selectionMode === INSTANCE_SELECTION_MODE.EXCLUDE &&
          selectedInstanceIds.length === 0) ||
        (selectionMode === INSTANCE_SELECTION_MODE.INCLUDE &&
          selectedInstanceIds.length === filteredInstancesCount &&
          filteredInstancesCount !== 0)
      ) {
        this.setMode(INSTANCE_SELECTION_MODE.ALL);
        this.setAllChecked(true);
        this.setSelectedInstanceIds([]);
      }
    });
  }

  setMode(mode: Mode) {
    this.state.selectionMode = mode;
  }

  setAllChecked(isAllChecked: boolean) {
    this.state.isAllChecked = isAllChecked;
  }

  setSelectedInstanceIds(ids: string[]) {
    this.state.selectedInstanceIds = ids;
  }

  addToSelectedInstanceIds = (id: string) => {
    this.setSelectedInstanceIds([...this.state.selectedInstanceIds, id]);
  };

  removeFromSelectedInstanceIds = (id: string) => {
    this.setSelectedInstanceIds(
      this.state.selectedInstanceIds.filter((prevId) => prevId !== id)
    );
  };

  isInstanceChecked = (id: string) => {
    const {selectionMode, selectedInstanceIds} = this.state;

    switch (selectionMode) {
      case INSTANCE_SELECTION_MODE.INCLUDE:
        return selectedInstanceIds.indexOf(id) >= 0;
      case INSTANCE_SELECTION_MODE.EXCLUDE:
        return selectedInstanceIds.indexOf(id) < 0;
      default:
        return selectionMode === INSTANCE_SELECTION_MODE.ALL;
    }
  };

  selectAllInstances = () => {
    if (this.state.selectionMode === INSTANCE_SELECTION_MODE.ALL) {
      this.setMode(INSTANCE_SELECTION_MODE.INCLUDE);
      this.setAllChecked(false);
    } else {
      this.setMode(INSTANCE_SELECTION_MODE.ALL);
      this.setAllChecked(true);
      this.setSelectedInstanceIds([]);
    }
  };

  selectInstance = (id: string) => {
    const {selectionMode, selectedInstanceIds} = this.state;

    if (selectionMode === INSTANCE_SELECTION_MODE.ALL) {
      this.setMode(INSTANCE_SELECTION_MODE.EXCLUDE);
      this.setAllChecked(false);
    }

    if (selectedInstanceIds.indexOf(id) >= 0) {
      this.removeFromSelectedInstanceIds(id);
    } else {
      this.addToSelectedInstanceIds(id);
    }
  };

  getSelectedInstanceCount = () => {
    const {selectionMode, selectedInstanceIds} = this.state;
    const {filteredInstancesCount} = instancesStore.state;

    switch (selectionMode) {
      case INSTANCE_SELECTION_MODE.INCLUDE:
        return selectedInstanceIds.length;
      case INSTANCE_SELECTION_MODE.EXCLUDE:
        return (filteredInstancesCount ?? 0) - selectedInstanceIds.length;
      default:
        return filteredInstancesCount;
    }
  };

  get selectedInstanceIds() {
    const {selectionMode, selectedInstanceIds} = this.state;

    return selectionMode === INSTANCE_SELECTION_MODE.INCLUDE
      ? selectedInstanceIds
      : [];
  }

  get excludedInstanceIds() {
    const {selectionMode, selectedInstanceIds} = this.state;

    return selectionMode === INSTANCE_SELECTION_MODE.EXCLUDE
      ? selectedInstanceIds
      : [];
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

export const instanceSelectionStore = new InstanceSelection();
