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
  action,
  type IReactionDisposer,
} from 'mobx';

type Runtime = {
  totalCount: number;
  visibleIds: string[];
  visibleRunningIds?: string[];
  visibleFinishedIds?: string[];
};

type Mode = 'INCLUDE' | 'EXCLUDE' | 'ALL';
type State = {
  selectedIds: string[];
  selectionMode: Mode;
};

const DEFAULT_STATE: State = {
  selectedIds: [],
  selectionMode: 'INCLUDE',
};

class InstancesSelection {
  state: State = {...DEFAULT_STATE};
  runtime: Runtime = {
    totalCount: 0,
    visibleIds: [],
    visibleRunningIds: [],
    visibleFinishedIds: [],
  };
  autorunDisposer: null | IReactionDisposer = null;

  constructor() {
    makeAutoObservable(this);
  }

  init() {
    this.autorunDisposer = autorun(() => {
      const {selectionMode, selectedIds} = this.state;
      const {totalCount} = this.runtime;

      if (
        (selectionMode === 'EXCLUDE' && selectedIds.length === 0) ||
        (selectionMode === 'INCLUDE' &&
          selectedIds.length === totalCount &&
          totalCount !== 0)
      ) {
        this.#setMode('ALL');
        this.#setSelectedIds([]);
      }
    });
  }

  setRuntime(next: Runtime) {
    const prev = this.runtime;

    if (
      prev.totalCount === next.totalCount &&
      isEqual(prev.visibleIds, next.visibleIds) &&
      isEqual(prev.visibleRunningIds ?? [], next.visibleRunningIds ?? []) &&
      isEqual(prev.visibleFinishedIds ?? [], next.visibleFinishedIds ?? [])
    ) {
      return;
    }

    this.runtime = next;
  }

  #setMode = action((mode: Mode) => {
    this.state.selectionMode = mode;
  });

  #setSelectedIds = action((ids: string[]) => {
    this.state.selectedIds = ids;
  });

  #addToSelectedIds = (id: string) => {
    this.#setSelectedIds([...this.state.selectedIds, id]);
  };

  #removeFromSelectedIds = (id: string) => {
    this.#setSelectedIds(
      this.state.selectedIds.filter((prevId) => prevId !== id),
    );
  };

  isChecked = (id: string) => {
    const {selectionMode, selectedIds} = this.state;

    switch (selectionMode) {
      case 'INCLUDE':
        return selectedIds.indexOf(id) >= 0;
      case 'EXCLUDE':
        return selectedIds.indexOf(id) < 0;
      default:
        return selectionMode === 'ALL';
    }
  };

  selectAll = () => {
    if (this.state.selectionMode === 'INCLUDE' && this.selectedCount === 0) {
      this.#setMode('ALL');
    } else {
      this.#setMode('INCLUDE');
      this.#setSelectedIds([]);
    }
  };

  select = (id: string) => {
    const {selectionMode, selectedIds} = this.state;

    if (selectionMode === 'ALL') {
      this.#setMode('EXCLUDE');
    }

    if (selectedIds.indexOf(id) >= 0) {
      this.#removeFromSelectedIds(id);

      if (selectionMode === 'EXCLUDE' && this.state.selectedIds.length === 0) {
        this.#setMode('ALL');
      }
    } else {
      this.#addToSelectedIds(id);
    }
  };

  get selectedCount() {
    const {selectionMode, selectedIds} = this.state;
    const {totalCount} = this.runtime;

    switch (selectionMode) {
      case 'INCLUDE':
        return selectedIds.length;
      case 'EXCLUDE':
        return (totalCount ?? 0) - selectedIds.length;
      default:
        return totalCount;
    }
  }

  get selectedIds() {
    const {selectionMode, selectedIds} = this.state;

    return selectionMode === 'INCLUDE' ? selectedIds : [];
  }

  get isAllChecked(): boolean {
    return this.state.selectionMode === 'ALL';
  }

  get hasSelectedRunningInstances() {
    const {
      selectedIds,
      isAllChecked,
      state: {selectionMode},
    } = this;
    const visibleRunningIds = this.runtime.visibleRunningIds ?? [];

    return (
      isAllChecked ||
      selectionMode === 'EXCLUDE' ||
      visibleRunningIds.some((id) => selectedIds.includes(id))
    );
  }

  get hasSelectedFinishedInstances() {
    const {
      selectedIds,
      isAllChecked,
      state: {selectionMode},
    } = this;
    const visibleFinishedIds = this.runtime.visibleFinishedIds ?? [];

    return (
      isAllChecked ||
      selectionMode === 'EXCLUDE' ||
      visibleFinishedIds.some((id) => selectedIds.includes(id))
    );
  }

  get checkedRunningIds() {
    const {selectionMode, selectedIds} = this.state;
    const visibleRunningIds = this.runtime.visibleRunningIds ?? [];

    if (selectionMode === 'INCLUDE') {
      return selectedIds.filter((id) => visibleRunningIds.includes(id));
    }

    return visibleRunningIds.filter((id) => !selectedIds.includes(id));
  }

  get checkedFinishedIds() {
    const {selectionMode, selectedIds} = this.state;
    const visibleFinishedIds = this.runtime.visibleFinishedIds ?? [];

    if (selectionMode === 'INCLUDE') {
      return selectedIds.filter((id) => visibleFinishedIds.includes(id));
    }

    return visibleFinishedIds.filter((id) => !selectedIds.includes(id));
  }

  get checkedIds() {
    const {selectionMode, selectedIds} = this.state;
    const {visibleIds} = this.runtime;

    if (selectionMode === 'INCLUDE') {
      return selectedIds;
    }

    return visibleIds.filter((id) => !selectedIds.includes(id));
  }

  get excludedIds() {
    const {selectionMode, selectedIds} = this.state;

    return selectionMode === 'EXCLUDE' ? selectedIds : [];
  }

  resetState = () => {
    this.#setSelectedIds([]);
    this.#setMode('INCLUDE');
  };

  reset = () => {
    this.resetState();
    this.runtime = {
      totalCount: 0,
      visibleIds: [],
      visibleRunningIds: [],
      visibleFinishedIds: [],
    };
    this.autorunDisposer?.();
  };
}

const processInstancesSelectionStore = new InstancesSelection();
const decisionInstancesSelectionStore = new InstancesSelection();

export {processInstancesSelectionStore, decisionInstancesSelectionStore};
