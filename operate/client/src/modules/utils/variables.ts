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
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
import {applyOperation} from 'modules/api/processInstances/operations';

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
      getScopeId() !== null
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
    () => getScopeId(),
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

const getScopeId = () => {
  const {selection} = flowNodeSelectionStore.state;
  const {metaData} = flowNodeMetaDataStore.state;

  return selection?.flowNodeInstanceId ?? metaData?.flowNodeInstanceId ?? null;
};

const addVariable = async ({
  id,
  name,
  value,
  invalidateQueries,
  onSuccess,
  onError,
}: {
  id: string;
  name: string;
  value: string;
  invalidateQueries: () => void;
  onSuccess: () => void;
  onError: (statusCode: number) => void;
}) => {
  variablesStore.setPendingItem({
    name,
    value,
    hasActiveOperation: true,
    isFirst: false,
    sortValues: null,
    isPreview: false,
  });

  const response = await applyOperation(id, {
    operationType: 'ADD_VARIABLE',
    variableScopeId: getScopeId() || undefined,
    variableName: name,
    variableValue: value,
  });

  invalidateQueries();

  if (response.isSuccess) {
    const {id} = response.data;
    variablesStore.startPollingOperation({operationId: id, onSuccess, onError});
    return 'SUCCESSFUL';
  } else {
    variablesStore.setPendingItem(null);
    if (response.statusCode === 400) {
      return 'VALIDATION_ERROR';
    }

    onError(response.statusCode);
    return 'FAILED';
  }
};

const updateVariable = async ({
  id,
  name,
  value,
  invalidateQueries,
  onError,
}: {
  id: string;
  name: string;
  value: string;
  invalidateQueries: () => void;
  onError: (statusCode: number) => void;
}) => {
  const response = await applyOperation(id, {
    operationType: 'UPDATE_VARIABLE',
    variableScopeId: getScopeId() || undefined,
    variableName: name,
    variableValue: value,
  });

  invalidateQueries();

  if (!response.isSuccess) {
    onError(response.statusCode);
  }
};

export {init, startPolling, getScopeId, addVariable, updateVariable};
