/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {observable, decorate, action, computed} from 'mobx';
import {fetchWorkflowInstance} from 'modules/api/instances';
import {getWorkflowName} from 'modules/utils/instance';

import {PAGE_TITLE} from 'modules/constants';

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

  get workflowTitle() {
    if (this.state.instance === null) {
      return null;
    }

    return PAGE_TITLE.INSTANCE(
      this.state.instance.id,
      getWorkflowName(this.state.instance)
    );
  }

  reset = () => {
    this.state = {...DEFAULT_STATE};
  };
}

decorate(CurrentInstance, {
  state: observable,
  reset: action,
  setCurrentInstance: action,
  workflowTitle: computed,
});

export const currentInstance = new CurrentInstance();
