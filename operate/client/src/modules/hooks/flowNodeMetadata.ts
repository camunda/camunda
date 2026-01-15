/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {
  useHasRunningOrFinishedTokens,
  useNewTokenCountForSelectedNode,
} from './flowNodeSelection';
import {useElementInstancesCount} from './useElementInstancesCount';
import {useProcessInstanceElementSelection} from './useProcessInstanceElementSelection';
import {IS_ELEMENT_SELECTION_V2} from 'modules/feature-flags';

const useHasMultipleInstances = () => {
  const hasMultipleInstancesV2 = useHasMultipleInstancesV2();
  const hasMultipleInstancesV1 = useHasMultipleInstancesV1();

  if (IS_ELEMENT_SELECTION_V2) {
    return hasMultipleInstancesV2;
  }

  return hasMultipleInstancesV1;
};

const useHasMultipleInstancesV1 = () => {
  const hasRunningOrFinishedTokens = useHasRunningOrFinishedTokens();
  const newTokenCountForSelectedNode = useNewTokenCountForSelectedNode();
  const elementInstancesCount = useElementInstancesCount(
    flowNodeSelectionStore.state.selection?.flowNodeId,
  );

  if (flowNodeSelectionStore.state.selection?.isMultiInstance) {
    return false;
  }

  if (
    flowNodeSelectionStore.state.selection?.flowNodeInstanceId !== undefined
  ) {
    return false;
  }

  if (!hasRunningOrFinishedTokens) {
    return newTokenCountForSelectedNode > 1;
  }

  return (elementInstancesCount ?? 0) > 1 || newTokenCountForSelectedNode > 0;
};

const useHasMultipleInstancesV2 = () => {
  const hasRunningOrFinishedTokens = useHasRunningOrFinishedTokens();
  const newTokenCountForSelectedNode = useNewTokenCountForSelectedNode();
  const {
    isSelectedInstanceMultiInstanceBody,
    selectedElementId,
    selectedElementInstanceKey,
  } = useProcessInstanceElementSelection();
  const elementInstancesCount = useElementInstancesCount(
    selectedElementId ?? undefined,
  );

  if (isSelectedInstanceMultiInstanceBody) {
    return false;
  }

  if (selectedElementInstanceKey !== null) {
    return false;
  }

  if (!hasRunningOrFinishedTokens) {
    return newTokenCountForSelectedNode > 1;
  }

  return (elementInstancesCount ?? 0) > 1 || newTokenCountForSelectedNode > 0;
};

export {useHasMultipleInstances};
