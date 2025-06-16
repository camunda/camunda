/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {logger} from 'modules/logger';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {modificationsStore} from 'modules/stores/modifications';
import {variablesStore} from 'modules/stores/variables';
import {
  useHasRunningOrFinishedTokens,
  useIsRootNodeSelected,
  useNewTokenCountForSelectedNode,
} from './flowNodeSelection';
import {useHasMultipleInstances} from './flowNodeMetadata';
import {getScopeId} from 'modules/utils/variables';

const useHasNoContent = () => {
  const newTokenCountForSelectedNode = useNewTokenCountForSelectedNode();
  const hasRunningOrFinishedTokens = useHasRunningOrFinishedTokens();
  const isRootNodeSelected = useIsRootNodeSelected();

  return (
    !isRootNodeSelected &&
    !hasRunningOrFinishedTokens &&
    newTokenCountForSelectedNode === 0
  );
};

/**
 * DEPRECATED: This hook is being migrated as part of Operate UI Migration. Use `useDisplayStatus` instead.
 **/
const useDisplayStatusFromVariablesStore = () => {
  const hasNoContent = useHasNoContent();
  const hasMultipleInstances = useHasMultipleInstances();
  const newTokenCountForSelectedNode = useNewTokenCountForSelectedNode();
  const {status, items} = variablesStore.state;

  if (status === 'error') {
    return 'error';
  }

  if (hasNoContent) {
    return 'no-content';
  }

  if (hasMultipleInstances) {
    return 'multi-instances';
  }

  if (
    flowNodeSelectionStore.state.selection?.isPlaceholder ||
    newTokenCountForSelectedNode === 1
  ) {
    return 'no-variables';
  }

  if (['initial', 'first-fetch'].includes(status)) {
    return variablesStore.areVariablesLoadedOnce ? 'spinner' : 'skeleton';
  }

  if (modificationsStore.isModificationModeEnabled && getScopeId() === null) {
    return 'no-variables';
  }
  if (status === 'fetching' || getScopeId() === null) {
    return 'spinner';
  }
  if (variablesStore.hasNoVariables) {
    return 'no-variables';
  }
  if (
    ['fetched', 'fetching-next', 'fetching-prev'].includes(status) &&
    items.length > 0
  ) {
    return 'variables';
  }

  logger.error('Failed to show Variables');
  return 'error';
};

const useDisplayStatus = ({
  isLoading,
  isFetchingNextPage,
  isFetchingPreviousPage,
  isFetched,
  isError,
  hasItems,
}: {
  isLoading: boolean;
  isFetchingNextPage: boolean;
  isFetchingPreviousPage: boolean;
  isFetched: boolean;
  isError: boolean;
  hasItems: boolean;
}) => {
  const hasNoContent = useHasNoContent();
  const hasMultipleInstances = useHasMultipleInstances();
  const newTokenCountForSelectedNode = useNewTokenCountForSelectedNode();

  if (isError) {
    return 'error';
  }

  if (hasNoContent) {
    return 'no-variables';
  }

  if (hasMultipleInstances) {
    return 'multi-instances';
  }

  if (
    flowNodeSelectionStore.state.selection?.isPlaceholder ||
    newTokenCountForSelectedNode === 1
  ) {
    return 'no-variables';
  }

  if (modificationsStore.isModificationModeEnabled && getScopeId() === null) {
    return 'no-variables';
  }
  if (isLoading || getScopeId() === null) {
    return 'spinner';
  }
  if (!hasItems) {
    return 'no-variables';
  }
  if ((isFetched || isFetchingNextPage || isFetchingPreviousPage) && hasItems) {
    return 'variables';
  }

  logger.error('Failed to show Variables');
  return 'error';
};

export {useDisplayStatusFromVariablesStore, useDisplayStatus};
