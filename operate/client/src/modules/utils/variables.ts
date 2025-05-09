/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ProcessInstance} from '@vzeta/camunda-api-zod-schemas/operate';
import {autorun, reaction, when} from 'mobx';
import {MAX_VARIABLES_PER_REQUEST} from 'modules/constants/variables';
import {modificationsStore} from 'modules/stores/modifications';
import {variablesStore} from 'modules/stores/variables';
import {isInstanceRunning, isRunning} from './instance';

const init = (processInstance?: ProcessInstance) => {
  variablesStore.instanceId = processInstance?.processInstanceKey || null;

  variablesStore.variablesWithActiveOperationsDisposer = when(
    () => processInstance?.state === 'TERMINATED',
    variablesStore.removeVariablesWithActiveOperations,
  );

  variablesStore.disposer = autorun(() => {
    if (
      processInstance &&
      isRunning(processInstance) &&
      variablesStore.scopeId !== null
    ) {
      if (
        variablesStore.intervalId === null &&
        !modificationsStore.isModificationModeEnabled
      ) {
        startPolling(processInstance);
      }
    } else {
      variablesStore.stopPolling();
    }
  });

  variablesStore.fetchVariablesDisposer = reaction(
    () => variablesStore.scopeId,
    (scopeId) => {
      variablesStore.clearItems();

      if (scopeId !== null) {
        variablesStore.setPendingItem(null);
        variablesStore.fetchAbortController?.abort();

        variablesStore.fetchVariables({
          fetchType: 'initial',
          instanceId: processInstance?.processInstanceKey,
          payload: {
            pageSize: MAX_VARIABLES_PER_REQUEST,
            scopeId: scopeId ?? processInstance?.processInstanceKey,
          },
        });
      }
    },
    {fireImmediately: true},
  );

  variablesStore.deleteFullVariablesDisposer = reaction(
    () => modificationsStore.isModificationModeEnabled,
    (isModification, prevIsModification) => {
      if (!isModification && prevIsModification) {
        variablesStore.clearFullVariableValues();
      }
    },
  );
};

const startPolling = async (
  processInstance?: ProcessInstance,
  options: {runImmediately?: boolean} = {runImmediately: false},
) => {
  if (
    document.visibilityState === 'hidden' ||
    (processInstance && !isInstanceRunning(processInstance))
  ) {
    return;
  }

  if (options.runImmediately && processInstance) {
    variablesStore.handlePolling(processInstance.processInstanceKey);
  }

  variablesStore.intervalId = setInterval(() => {
    if (!variablesStore.isPollRequestRunning && processInstance) {
      variablesStore.handlePolling(processInstance.processInstanceKey);
    }
  }, 5000);
};

export {init, startPolling};
