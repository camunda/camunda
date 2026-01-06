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

const useHasMultipleInstances = () => {
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

export {useHasMultipleInstances};
