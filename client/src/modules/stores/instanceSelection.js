/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {observable, decorate, action, computed, autorun, observe} from 'mobx';
import {instances} from 'modules/stores/instances';
import {filters} from 'modules/stores/filters';
import {INSTANCE_SELECTION_MODE} from 'modules/constants';
import {isEqual} from 'lodash';

const DEFAULT_STATE = {
  selectedInstanceIds: [],
  isAllChecked: false,
  selectionMode: INSTANCE_SELECTION_MODE.INCLUDE,
};

class InstanceSelection {
  state = {...DEFAULT_STATE};
  autorunDisposer = null;
  observeDisposer = null;

  init() {
    const {selectionMode, selectedInstanceIds} = this.state;
    const {filteredInstancesCount} = instances.state;

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

    this.observeDisposer = observe(filters.state, 'filter', (change) => {
      if (isEqual(filters.state.filter, change.oldValue)) {
        return;
      }

      this.resetState();
    });
  }

  setMode(mode) {
    this.state.selectionMode = mode;
  }

  setAllChecked(isAllChecked) {
    this.state.isAllChecked = isAllChecked;
  }

  setSelectedInstanceIds(ids) {
    this.state.selectedInstanceIds = ids;
  }

  addToSelectedInstanceIds = (id) => {
    this.setSelectedInstanceIds([...this.state.selectedInstanceIds, id]);
  };

  removeFromSelectedInstanceIds = (id) => {
    this.setSelectedInstanceIds(
      this.state.selectedInstanceIds.filter((prevId) => prevId !== id)
    );
  };

  isInstanceChecked = (id) => {
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

  selectInstance = (id) => {
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
    const {filteredInstancesCount} = instances.state;

    switch (selectionMode) {
      case INSTANCE_SELECTION_MODE.INCLUDE:
        return selectedInstanceIds.length;
      case INSTANCE_SELECTION_MODE.EXCLUDE:
        return filteredInstancesCount - selectedInstanceIds.length;
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
    if (this.autorunDisposer !== null) {
      this.autorunDisposer();
    }
    if (this.observeDisposer !== null) {
      this.observeDisposer();
    }
  };
}

decorate(InstanceSelection, {
  state: observable,
  setMode: action,
  setAllChecked: action,
  setSelectedInstanceIds: action,
  addToSelectedInstanceIds: action,
  removeFromSelectedInstanceIds: action,
  resetState: action,
  selectedInstanceIds: computed,
  excludedInstanceIds: computed,
});

export const instanceSelection = new InstanceSelection();
