/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {makeObservable, observable} from 'mobx';
import {Alert, fetchAlerts, setAlert} from 'modules/api/v2/incident-alerting';

type State = {
  alerts?: Alert[];
};

const DEFAULT_STATE: State = {
  alerts: undefined,
};

class Alerts {
  state: State = {...DEFAULT_STATE};

  async init() {
    const response = await fetchAlerts();

    if (response.isSuccess) {
      this.setAlerts(response.data);
    }
  }

  postAlert = async (processDefinitionKey: string, email: string) => {
    await setAlert({processDefinitionKey, email});
  };

  setAlerts(alerts: Alert[]) {
    this.state.alerts = alerts;
  }

  constructor() {
    makeObservable(this, {
      state: observable,
    });
  }

  reset = () => {
    this.state = {...DEFAULT_STATE};
  };
}

export const alertsStore = new Alerts();
