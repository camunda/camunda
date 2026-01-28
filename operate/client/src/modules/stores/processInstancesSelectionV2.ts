/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import isEqual from 'lodash/isEqual';
import {
  makeAutoObservable,
  autorun,
  type IReactionDisposer,
  type Lambda,
} from 'mobx';

type SelectionRuntime = {
  totalProcessInstancesCount: number;
  visibleIds: string[];
  visibleRunningIds: string[];
};

type Mode = 'INCLUDE' | 'EXCLUDE' | 'ALL';
type State = {
  selectedProcessInstanceIds: string[];
  selectionMode: Mode;
};

const DEFAULT_STATE: State = {
  selectedProcessInstanceIds: [],
  selectionMode: 'INCLUDE',
};

class ProcessInstancesSelection {
  state: State = {...DEFAULT_STATE};
  runtime: SelectionRuntime = {
    totalProcessInstancesCount: 0,
    visibleIds: [],
    visibleRunningIds: [],
  };
  autorunDisposer: null | IReactionDisposer = null;
  observeDisposer: null | Lambda = null;

  constructor() {
    makeAutoObservable(this);
  }

  init() {
    this.autorunDisposer = autorun(() => {
      const {selectionMode, selectedProcessInstanceIds} = this.state;
      const {totalProcessInstancesCount} = this.runtime;

      if (
        (selectionMode === 'EXCLUDE' &&
          selectedProcessInstanceIds.length === 0) ||
        (selectionMode === 'INCLUDE' &&
          selectedProcessInstanceIds.length === totalProcessInstancesCount &&
          totalProcessInstancesCount !== 0)
      ) {
        this.#setMode('ALL');
        this.#setSelectedProcessInstanceIds([]);
      }
    });
  }

  setRuntime(next: SelectionRuntime) {
    const prev = this.runtime;

    if (
      prev.totalProcessInstancesCount === next.totalProcessInstancesCount &&
      isEqual(prev.visibleIds, next.visibleIds) &&
      isEqual(prev.visibleRunningIds, next.visibleRunningIds)
    ) {
      return;
    }

    this.runtime = next;
  }

  #setMode(mode: Mode) {
    this.state.selectionMode = mode;
  }

  #setSelectedProcessInstanceIds(ids: string[]) {
    this.state.selectedProcessInstanceIds = ids;
  }

  #addToSelectedProcessInstanceIds = (id: string) => {
    this.#setSelectedProcessInstanceIds([
      ...this.state.selectedProcessInstanceIds,
      id,
    ]);
  };

  #removeFromSelectedProcessInstanceIds = (id: string) => {
    this.#setSelectedProcessInstanceIds(
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
      this.#setMode('ALL');
    } else {
      this.#setMode('INCLUDE');
      this.#setSelectedProcessInstanceIds([]);
    }
  };

  selectProcessInstance = (id: string) => {
    const {selectionMode, selectedProcessInstanceIds} = this.state;

    if (selectionMode === 'ALL') {
      this.#setMode('EXCLUDE');
    }

    if (selectedProcessInstanceIds.indexOf(id) >= 0) {
      this.#removeFromSelectedProcessInstanceIds(id);

      if (
        selectionMode === 'EXCLUDE' &&
        this.state.selectedProcessInstanceIds.length === 0
      ) {
        this.#setMode('ALL');
      }
    } else {
      this.#addToSelectedProcessInstanceIds(id);
    }
  };

  get selectedProcessInstanceCount() {
    const {selectionMode, selectedProcessInstanceIds} = this.state;
    const {totalProcessInstancesCount} = this.runtime;

    switch (selectionMode) {
      case 'INCLUDE':
        return selectedProcessInstanceIds.length;
      case 'EXCLUDE':
        return (
          (totalProcessInstancesCount ?? 0) - selectedProcessInstanceIds.length
        );
      default:
        return totalProcessInstancesCount;
    }
  }

  get selectedProcessInstanceIds() {
    const {selectionMode, selectedProcessInstanceIds} = this.state;

    return selectionMode === 'INCLUDE' ? selectedProcessInstanceIds : [];
  }

  get isAllChecked(): boolean {
    return this.state.selectionMode === 'ALL';
  }

  get hasSelectedRunningInstances() {
    const {
      selectedProcessInstanceIds,
      isAllChecked,
      state: {selectionMode},
    } = this;
    const {visibleRunningIds} = this.runtime;

    return (
      isAllChecked ||
      selectionMode === 'EXCLUDE' ||
      visibleRunningIds.some((id) => selectedProcessInstanceIds.includes(id))
    );
  }

  get checkedRunningProcessInstanceIds() {
    const {selectionMode, selectedProcessInstanceIds} = this.state;
    const {visibleRunningIds} = this.runtime;

    if (selectionMode === 'INCLUDE') {
      return selectedProcessInstanceIds.filter((id) =>
        visibleRunningIds.includes(id),
      );
    }

    return visibleRunningIds.filter(
      (id) => !selectedProcessInstanceIds.includes(id),
    );
  }

  get checkedProcessInstanceIds() {
    const {selectionMode, selectedProcessInstanceIds} = this.state;
    const {visibleIds} = this.runtime;

    if (selectionMode === 'INCLUDE') {
      return selectedProcessInstanceIds;
    }

    return visibleIds.filter((id) => !selectedProcessInstanceIds.includes(id));
  }

  get excludedProcessInstanceIds() {
    const {selectionMode, selectedProcessInstanceIds} = this.state;

    return selectionMode === 'EXCLUDE' ? selectedProcessInstanceIds : [];
  }

  resetState = () => {
    this.state.selectedProcessInstanceIds = [];
    this.state.selectionMode = 'INCLUDE';
  };

  reset = () => {
    this.resetState();
    this.runtime = {
      totalProcessInstancesCount: 0,
      visibleIds: [],
      visibleRunningIds: [],
    };
    this.autorunDisposer?.();
    this.observeDisposer?.();
  };
}

export const processInstancesSelectionStore = new ProcessInstancesSelection();
