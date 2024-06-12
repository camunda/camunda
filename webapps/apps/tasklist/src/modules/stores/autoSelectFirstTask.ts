/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {action, makeObservable, observable} from 'mobx';
import {getStateLocally, storeStateLocally} from 'modules/utils/localStorage';

class AutoSelectFirstTask {
  enabled: boolean;

  constructor() {
    makeObservable(this, {
      enabled: observable,
      toggle: action.bound,
    });
    this.enabled = getStateLocally('autoSelectNextTask') ?? false;
  }

  toggle() {
    storeStateLocally('autoSelectNextTask', !this.enabled);
    this.enabled = !this.enabled;
  }
}

const autoSelectNextTaskStore = new AutoSelectFirstTask();

export {autoSelectNextTaskStore};
