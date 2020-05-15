/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {observable, decorate, action} from 'mobx';
import {storeStateLocally, getStateLocally} from 'modules/utils/localStorage';

const DEFAULT_STATE = {
  filteredInstancesCount: null,
  workflowInstances: [],
};

class Instances {
  state = {...DEFAULT_STATE};

  constructor() {
    this.state.filteredInstancesCount =
      getStateLocally().filteredInstancesCount || null;
  }

  setInstances = ({filteredInstancesCount, workflowInstances}) => {
    this.state = {
      workflowInstances,
      filteredInstancesCount,
    };
    storeStateLocally({filteredInstancesCount});
  };

  reset = () => {
    this.state = {...DEFAULT_STATE};
  };
}

decorate(Instances, {
  state: observable,
  reset: action,
  setInstances: action,
});

export const instances = new Instances();
