/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {logger} from 'modules/logger';
import {modificationsStore} from 'modules/stores/modifications';
import {
  useHasRunningOrFinishedTokens,
  useIsPlaceholderSelected,
  useIsRootNodeSelected,
  useNewTokenCountForSelectedNode,
} from './flowNodeSelection';
import {useHasMultipleInstances} from './flowNodeMetadata';
import {useProcessInstanceElementSelection} from './useProcessInstanceElementSelection';
import {TOKEN_OPERATIONS} from 'modules/constants';
import {useElementSelectionInstanceKey} from './useElementSelectionInstanceKey';
import {IS_ELEMENT_SELECTION_V2} from 'modules/feature-flags';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';

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

const useVariableScopeKey = (fallback?: string | null) => {
  const selectedInstanceKey = useElementSelectionInstanceKey();
  const {selectedElementId} = useProcessInstanceElementSelection();

  if (selectedInstanceKey) {
    return selectedInstanceKey;
  }

  // In modification mode, if selecting from diagram, check for pending ADD_TOKEN
  if (modificationsStore.state.status === 'enabled' && selectedElementId) {
    const addTokenModification = modificationsStore.flowNodeModifications.find(
      (modification) =>
        modification.operation === TOKEN_OPERATIONS.ADD_TOKEN &&
        modification.flowNode.id === selectedElementId,
    );

    if (addTokenModification && 'scopeId' in addTokenModification) {
      return addTokenModification.scopeId;
    }
  }

  return fallback ?? null;
};

const useDisplayStatus = ({
  scopeKey,
  isLoading,
  isFetchingNextPage,
  isFetchingPreviousPage,
  isFetched,
  isError,
  hasItems,
}: {
  scopeKey: string | null;
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
  let isPlaceholderSelected = useIsPlaceholderSelected();
  let {isFetchingElementError} = useProcessInstanceElementSelection();

  // TODO: Remove these assignments once the feature flag is fully rolled out
  isFetchingElementError = IS_ELEMENT_SELECTION_V2
    ? isFetchingElementError
    : flowNodeMetaDataStore.state.status === 'error';
  isPlaceholderSelected = IS_ELEMENT_SELECTION_V2
    ? isPlaceholderSelected
    : flowNodeSelectionStore.state.selection?.isPlaceholder ||
      newTokenCountForSelectedNode === 1;

  if (isError || isFetchingElementError) {
    return 'error';
  }

  if (hasNoContent) {
    return 'no-variables';
  }

  if (hasMultipleInstances) {
    return 'multi-instances';
  }

  if (isPlaceholderSelected) {
    return 'no-variables';
  }

  if (modificationsStore.isModificationModeEnabled && scopeKey === null) {
    return 'no-variables';
  }
  if (isLoading || scopeKey === null) {
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

export {useDisplayStatus, useVariableScopeKey};
