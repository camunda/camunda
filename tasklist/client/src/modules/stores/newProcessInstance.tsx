/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {action, makeObservable, observable} from 'mobx';
import type {ProcessInstance} from 'modules/types';

type NewProcessInstanceType = ProcessInstance & {
  removeCallback: () => void;
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

  removeInstance = () => {
    this.instance?.removeCallback();
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
