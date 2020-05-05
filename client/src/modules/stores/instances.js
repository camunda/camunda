/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {observable, decorate, action} from 'mobx';

const DEFAULT_STATE = {
  filteredInstancesCount: null,
  workflowInstances: [],
};

class Instances {
  state = {...DEFAULT_STATE};

  setInstances = ({filteredInstancesCount, workflowInstances}) => {
    this.state = {filteredInstancesCount, workflowInstances};
  };
}

decorate(Instances, {
  state: observable,
  setInstances: action,
});

export const instances = new Instances();
