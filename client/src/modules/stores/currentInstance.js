/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {observable, decorate, action} from 'mobx';
import {fetchWorkflowInstance} from 'modules/api/instances';

const DEFAULT_STATE = {
  instance: null,
};

class CurrentInstance {
  state = {...DEFAULT_STATE};

  fetchCurrentInstance = async (id) => {
    const workflowInstance = await fetchWorkflowInstance(id);
    this.setCurrentInstance(workflowInstance);
  };

  setCurrentInstance = (currentInstance) => {
    this.state = {instance: currentInstance};
  };

  reset = () => {
    this.state = {...DEFAULT_STATE};
  };
}

decorate(CurrentInstance, {
  state: observable,
  reset: action,
  setCurrentInstance: action,
});

export const currentInstance = new CurrentInstance();
