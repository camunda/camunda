/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {makeAutoObservable} from 'mobx';
import {getStateLocally, storeStateLocally} from 'modules/utils/localStorage';

type State = {
  isFiltersCollapsed: boolean;
  isOperationsCollapsed: boolean;
};

const DEFAULT_STATE: State = {
  isFiltersCollapsed: false,
  isOperationsCollapsed: true,
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

  toggleFiltersPanel = () => {
    storeStateLocally(
      {
        isFiltersCollapsed: !this.state.isFiltersCollapsed,
      },
      'panelStates',
    );

    this.state.isFiltersCollapsed = !this.state.isFiltersCollapsed;
  };

  toggleOperationsPanel = () => {
    storeStateLocally(
      {
        isOperationsCollapsed: !this.state.isOperationsCollapsed,
      },
      'panelStates',
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
