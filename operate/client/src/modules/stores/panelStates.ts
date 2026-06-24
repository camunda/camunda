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
};

const DEFAULT_STATE: State = {
  isFiltersCollapsed: false,
};

class PanelStates {
  state: State = {...DEFAULT_STATE};

  constructor() {
    const {isFiltersCollapsed = false} = getStateLocally('panelStates');

    this.state.isFiltersCollapsed = isFiltersCollapsed;
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

  expandFiltersPanel = () => {
    storeStateLocally({isFiltersCollapsed: false}, 'panelStates');
    this.state.isFiltersCollapsed = false;
  };

  reset = () => {
    this.state = {...DEFAULT_STATE};
  };
}

export const panelStatesStore = new PanelStates();
