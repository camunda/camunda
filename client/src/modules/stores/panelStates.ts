/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {makeAutoObservable} from 'mobx';
import {getStateLocally, storeStateLocally} from 'modules/utils/localStorage';

type OperationsPanelRef = React.RefObject<HTMLElement> | null;

type State = {
  isFiltersCollapsed: boolean;
  isOperationsCollapsed: boolean;
  operationsPanelRef: OperationsPanelRef;
};

const DEFAULT_STATE: State = {
  isFiltersCollapsed: false,
  isOperationsCollapsed: true,
  operationsPanelRef: null,
};

class PanelStates {
  state: State = {...DEFAULT_STATE};

  constructor() {
    const {isFiltersCollapsed = false, isOperationsCollapsed = true} =
      getStateLocally('panelStates');

    this.state.isFiltersCollapsed = isFiltersCollapsed;
    this.state.isOperationsCollapsed = isOperationsCollapsed;
    makeAutoObservable(this);
  }

  setOperationsPanelRef = (ref: OperationsPanelRef) => {
    this.state.operationsPanelRef = ref;
  };

  toggleFiltersPanel = () => {
    storeStateLocally(
      {
        isFiltersCollapsed: !this.state.isFiltersCollapsed,
      },
      'panelStates'
    );

    this.state.isFiltersCollapsed = !this.state.isFiltersCollapsed;
  };

  toggleOperationsPanel = () => {
    storeStateLocally(
      {
        isOperationsCollapsed: !this.state.isOperationsCollapsed,
      },
      'panelStates'
    );

    this.state.isOperationsCollapsed = !this.state.isOperationsCollapsed;
  };

  expandFiltersPanel = () => {
    storeStateLocally({isFiltersCollapsed: false}, 'panelStates');
    this.state.isFiltersCollapsed = false;
  };

  expandOperationsPanel = () => {
    storeStateLocally({isOperationsCollapsed: false}, 'panelStates');
    this.state.isOperationsCollapsed = false;
  };

  reset = () => {
    this.state = {...DEFAULT_STATE};
  };
}

export const panelStatesStore = new PanelStates();
