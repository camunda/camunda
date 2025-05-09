/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {getFlowNodeName} from './flowNodes';
import {BusinessObjects} from 'bpmn-js/lib/NavigatedViewer';

const getSelectedRunningInstanceCount = (
  totalRunningInstancesForFlowNode: number,
) => {
  const currentSelection = flowNodeSelectionStore.state.selection;

  if (currentSelection === null) {
    return 0;
  }

  if (
    currentSelection.isPlaceholder ||
    flowNodeSelectionStore.isRootNodeSelected ||
    currentSelection.flowNodeId === undefined
  ) {
    return 0;
  }

  if (currentSelection.flowNodeInstanceId !== undefined) {
    return flowNodeMetaDataStore.isSelectedInstanceRunning ? 1 : 0;
  }

  return totalRunningInstancesForFlowNode;
};

const getSelectedFlowNodeName = (businessObjects?: BusinessObjects) => {
  if (
    processInstanceDetailsStore.state.processInstance === null ||
    flowNodeSelectionStore.state.selection === null
  ) {
    return '';
  }

  if (flowNodeSelectionStore.isRootNodeSelected) {
    return processInstanceDetailsStore.state.processInstance.processName;
  }

  if (flowNodeSelectionStore.state.selection.flowNodeId === undefined) {
    return '';
  }

  return getFlowNodeName({
    businessObjects,
    flowNodeId: flowNodeSelectionStore.state.selection.flowNodeId,
  });
};

export {getSelectedRunningInstanceCount, getSelectedFlowNodeName};
