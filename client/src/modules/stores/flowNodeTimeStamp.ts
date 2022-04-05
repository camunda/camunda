/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {makeAutoObservable} from 'mobx';

type State = {
  isTimeStampVisible: boolean;
};
const DEFAULT_STATE: State = {
  isTimeStampVisible: false,
};

class FlowNodeTimeStamp {
  state: State = {...DEFAULT_STATE};

  constructor() {
    makeAutoObservable(this);
  }

  toggleTimeStampVisibility = () => {
    this.state.isTimeStampVisible = !this.state.isTimeStampVisible;
  };

  reset = () => {
    this.state = {...DEFAULT_STATE};
  };
}

export const flowNodeTimeStampStore = new FlowNodeTimeStamp();
