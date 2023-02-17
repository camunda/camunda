/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {makeAutoObservable} from 'mobx';
import {getStateLocally, storeStateLocally} from 'modules/utils/localStorage';

type State = {
  panelState: 'closed' | 'maximized' | 'minimized';
  panelWidth: number | null;
};

const DEFAULT_STATE: State = {
  panelState: 'minimized',
  panelWidth: null,
};

class Drd {
  state: State = {...DEFAULT_STATE};

  constructor() {
    makeAutoObservable(this);
    this.init();
  }

  init = () => {
    const {drdPanelState = 'minimized', drdPanelWidth} =
      getStateLocally('panelStates');

    this.state.panelState = drdPanelState;
    this.state.panelWidth = drdPanelWidth ?? null;
  };

  setPanelState = (state: State['panelState']) => {
    storeStateLocally({drdPanelState: state}, 'panelStates');
    this.state.panelState = state;
  };

  setPanelWidth = (width: number) => {
    storeStateLocally({drdPanelWidth: width}, 'panelStates');
    this.state.panelWidth = width;
  };

  reset = () => {
    this.state = {...DEFAULT_STATE};
  };
}

export const drdStore = new Drd();
