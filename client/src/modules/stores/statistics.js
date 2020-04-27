/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {observable, decorate, action} from 'mobx';
import {fetchWorkflowCoreStatistics} from 'modules/api/instances';

const DEFAULT_STATE = {
  running: 0,
  active: 0,
  withIncidents: 0,
  isLoaded: false,
};

class Statistics {
  state = {...DEFAULT_STATE};

  fetchStatistics = async () => {
    const {coreStatistics} = await fetchWorkflowCoreStatistics();
    this.setCount(coreStatistics);
  };

  setCount = ({running, active, withIncidents}) => {
    this.state = {running, active, withIncidents, isLoaded: true};
  };
}

decorate(Statistics, {
  state: observable,
  reset: action,
  setCount: action,
});

export const statistics = new Statistics();
