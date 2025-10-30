/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {QueryVariablesResponseBody} from '@camunda/camunda-api-zod-schemas/8.8';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
<<<<<<< HEAD
import {applyOperation} from 'modules/api/processInstances/operations';
=======
import {modificationsStore} from 'modules/stores/modifications';
import {TOKEN_OPERATIONS} from 'modules/constants';
>>>>>>> 0c42c162 (refactor: remove dead code)
import type {InfiniteData} from '@tanstack/react-query';

const getScopeId = () => {
  const {selection} = flowNodeSelectionStore.state;
  const {metaData} = flowNodeMetaDataStore.state;

  return selection?.flowNodeInstanceId ?? metaData?.flowNodeInstanceId ?? null;
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

export {getScopeId, isTruncated, isPaginated, hasItems, variablesAsJSON};
