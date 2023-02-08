/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {action, makeObservable, observable} from 'mobx';
import {GetNewTasks} from 'modules/queries/get-new-tasks';
import type {ProcessInstance} from 'modules/types';

type NewProcessInstanceType = ProcessInstance & {
  removeCallback: (tasks: GetNewTasks['tasks'] | null) => void;
};

class NewProcessInstance {
  instance: NewProcessInstanceType | null = null;
  intervalId: NodeJS.Timeout | null = null;

  constructor() {
    makeObservable(this, {
      instance: observable,
      setInstance: action,
      removeInstance: action,
      reset: action,
    });
  }

  setInstance = (instance: NewProcessInstanceType) => {
    this.instance = instance;

    this.intervalId = setInterval(() => {
      this.removeInstance();
    }, 15000);
  };

  removeInstance = (tasks: GetNewTasks['tasks'] | null = []) => {
    this.instance?.removeCallback(tasks);
    this.instance = null;
    if (this.intervalId !== null) {
      clearTimeout(this.intervalId);
      this.intervalId = null;
    }
  };

  reset = this.removeInstance;
}

const newProcessInstance = new NewProcessInstance();

export {newProcessInstance};
