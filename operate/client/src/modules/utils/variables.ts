/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {QueryVariablesResponseBody} from '@camunda/camunda-api-zod-schemas/8.8';
import {variablesStore} from 'modules/stores/variables';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
import {modificationsStore} from 'modules/stores/modifications';
import {applyOperation} from 'modules/api/processInstances/operations';
import {TOKEN_OPERATIONS} from 'modules/constants';
import type {InfiniteData} from '@tanstack/react-query';

const getScopeId = () => {
  const {selection} = flowNodeSelectionStore.state;
  const {metaData} = flowNodeMetaDataStore.state;

  // First try to get actual instance ID
  const actualInstanceId =
    selection?.flowNodeInstanceId ?? metaData?.flowNodeInstanceId;
  if (actualInstanceId) {
    return actualInstanceId;
  }

  // In modification mode, if selecting from diagram, check for pending ADD_TOKEN
  if (modificationsStore.state.status === 'enabled' && selection?.flowNodeId) {
    const addTokenModification = modificationsStore.flowNodeModifications.find(
      (modification) =>
        modification.operation === TOKEN_OPERATIONS.ADD_TOKEN &&
        modification.flowNode.id === selection.flowNodeId,
    );

    if (addTokenModification && 'scopeId' in addTokenModification) {
      return addTokenModification.scopeId;
    }
  }

  return null;
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
  variablesStore.setPendingItem(null);
  setTimeout(() => invalidateQueries(), 5000);

  if (response.isSuccess) {
    onSuccess();
    return 'SUCCESSFUL';
  } else {
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

const isTruncated = (variables?: InfiniteData<QueryVariablesResponseBody>) => {
  return variables?.pages[0]?.items.some((item) => {
    return item.isTruncated;
  });
};

const isPaginated = (variables?: InfiniteData<QueryVariablesResponseBody>) => {
  return (variables?.pages && variables.pages.length >= 2) || false;
};

const hasItems = (variables?: InfiniteData<QueryVariablesResponseBody>) => {
  return variables?.pages[0]?.items && variables.pages[0]?.items.length > 0;
};

const variablesAsJSON = (
  variables?: InfiniteData<QueryVariablesResponseBody>,
) => {
  if (isPaginated(variables) || isTruncated(variables)) {
    return '{}';
  }

  try {
    const variableMap =
      variables?.pages?.flatMap((page) =>
        page.items.map((variable) => [
          variable.name,
          JSON.parse(variable.value),
        ]),
      ) ?? [];

    return JSON.stringify(Object.fromEntries(variableMap));
  } catch {
    console.error('Error: Variable can not be stringified');
    return '{}';
  }
};

export {
  getScopeId,
  addVariable,
  updateVariable,
  isTruncated,
  isPaginated,
  hasItems,
  variablesAsJSON,
};
