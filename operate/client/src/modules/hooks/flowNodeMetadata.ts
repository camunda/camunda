/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {useHasRunningOrFinishedTokens} from './flowNodeSelection';

const useHasMultipleInstances = () => {
  const hasRunningOrFinishedTokens = useHasRunningOrFinishedTokens();
  const {metaData} = flowNodeMetaDataStore.state;

  if (
    flowNodeSelectionStore.state.selection?.flowNodeInstanceId !== undefined
  ) {
    return false;
  }

  if (!hasRunningOrFinishedTokens) {
    return flowNodeSelectionStore.newTokenCountForSelectedNode > 1;
  }

  return (
    (metaData?.instanceCount ?? 0) > 1 ||
    flowNodeSelectionStore.newTokenCountForSelectedNode > 0
  );
};

export {useHasMultipleInstances};
