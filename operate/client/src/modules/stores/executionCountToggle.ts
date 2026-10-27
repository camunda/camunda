/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {makeAutoObservable} from 'mobx';

type State = {
  isExecutionCountVisible: boolean;
};
const DEFAULT_STATE: State = {
  isExecutionCountVisible: false,
};

class ExecutionCountToggle {
  state: State = {...DEFAULT_STATE};

  constructor() {
    makeAutoObservable(this);
  }

  toggleExecutionCountVisibility = () => {
    this.state.isExecutionCountVisible = !this.state.isExecutionCountVisible;
  };

  reset = () => {
    this.state = {...DEFAULT_STATE};
  };
}

export const executionCountToggleStore = new ExecutionCountToggle();
