/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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
