/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {makeAutoObservable} from 'mobx';
import {getStateLocally, storeStateLocally} from 'modules/utils/localStorage';

type State = {
  panelState: 'closed' | 'maximized' | 'minimized';
};

const DEFAULT_STATE: State = {
  panelState: 'minimized',
};

class Drd {
  state: State = {...DEFAULT_STATE};

  constructor() {
    const {drdPanelState = 'minimized'} = getStateLocally('panelStates');

    this.state.panelState = drdPanelState;
    makeAutoObservable(this);
  }

  setPanelState = (state: State['panelState']) => {
    storeStateLocally({drdPanelState: state}, 'panelStates');
    this.state.panelState = state;
  };

  reset = () => {
    this.state = {...DEFAULT_STATE};
  };
}

export const drdStore = new Drd();
