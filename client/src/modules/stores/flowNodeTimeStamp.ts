/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {observable, decorate, action} from 'mobx';

type State = {
  isTimeStampVisible: boolean;
};
const DEFAULT_STATE: State = {
  isTimeStampVisible: false,
};

class FlowNodeTimeStamp {
  state: State = {...DEFAULT_STATE};

  toggleTimeStampVisibility = () => {
    this.state.isTimeStampVisible = !this.state.isTimeStampVisible;
  };

  reset = () => {
    this.state = {...DEFAULT_STATE};
  };
}

decorate(FlowNodeTimeStamp, {
  state: observable,
  reset: action,
  toggleTimeStampVisibility: action,
});

export const flowNodeTimeStampStore = new FlowNodeTimeStamp();
