/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {observable, decorate, action} from 'mobx';

const DEFAULT_STATE = {
  selection: {
    treeRowIds: [],
    flowNodeId: null,
  },
};

class FlowNodeInstance {
  state = {...DEFAULT_STATE};

  setCurrentSelection = (selection) => {
    this.state = {selection};
  };

  reset = () => {
    this.state = {...DEFAULT_STATE};
  };
}

decorate(FlowNodeInstance, {
  state: observable,
  reset: action,
  setCurrentSelection: action,
});

export const flowNodeInstance = new FlowNodeInstance();
