/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ProcessInstance} from '@vzeta/camunda-api-zod-schemas/operate';
import {autorun} from 'mobx';
import {incidentsStore} from 'modules/stores/incidents';
import {isInstanceRunning} from './instance';

const init = (processInstance?: ProcessInstance) => {
  incidentsStore.disposer = autorun(() => {
    if (processInstance?.hasIncident) {
      if (incidentsStore.intervalId === null) {
        incidentsStore.fetchIncidents(processInstance.processInstanceKey);
        startPolling(processInstance);
      }
    } else {
      incidentsStore.stopPolling();
    }
  });
};

const startPolling = async (
  processInstance?: ProcessInstance,
  options: {runImmediately?: boolean} = {runImmediately: false},
) => {
  if (
    document.visibilityState === 'hidden' ||
    (processInstance && !isInstanceRunning(processInstance)) ||
    !processInstance?.hasIncident
  ) {
    return;
  }

  if (options.runImmediately) {
    if (!incidentsStore.isPollRequestRunning) {
      incidentsStore.handlePolling(processInstance.processInstanceKey);
    }
  }

  incidentsStore.intervalId = setInterval(() => {
    if (!incidentsStore.isPollRequestRunning) {
      incidentsStore.handlePolling(processInstance.processInstanceKey);
    }
  }, 5000);
};

export {init, startPolling};
