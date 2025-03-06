/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {action, makeObservable, observable} from 'mobx';
import {getStateLocally, storeStateLocally} from 'common/local-storage';

class AutoSelectFirstTask {
  enabled: boolean;

  constructor() {
    makeObservable(this, {
      enabled: observable,
      enable: action.bound,
      disable: action.bound,
    });
    this.enabled = getStateLocally('autoSelectNextTask') ?? false;
  }

  enable() {
    storeStateLocally('autoSelectNextTask', true);
    this.enabled = true;
  }

  disable() {
    storeStateLocally('autoSelectNextTask', false);
    this.enabled = false;
  }
}

const autoSelectNextTaskStore = new AutoSelectFirstTask();

export {autoSelectNextTaskStore};
