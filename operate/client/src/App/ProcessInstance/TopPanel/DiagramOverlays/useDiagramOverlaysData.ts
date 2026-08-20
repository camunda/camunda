/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo} from 'react';
import type {OverlayData} from 'modules/bpmn-js/overlayTypes';
import {useAgentInstancesStatusPerElement} from './agentInstances';
import {useElementStateOverlaysData} from './elementStateOverlay';
import {useModificationBadgeOverlaysData} from './modificationBadgeOverlay';
import {useWaitingStateOverlaysData} from './waitingStateOverlay';
import {useAgentStatusOverlaysData} from './agentStatusOverlay';
import {useAgentShineOverlaysData} from './agentShineOverlay';

const useDiagramOverlaysData = (
  isModificationModeEnabled: boolean,
): OverlayData[] => {
  const {agentInstancesStatusMap, elementsWithAgent} =
    useAgentInstancesStatusPerElement();

  const elementStateData = useElementStateOverlaysData();
  const modificationBadgeData = useModificationBadgeOverlaysData();
  const waitingStateData = useWaitingStateOverlaysData(elementsWithAgent);
  const agentStatusData = useAgentStatusOverlaysData(agentInstancesStatusMap);
  const agentShineData = useAgentShineOverlaysData(agentInstancesStatusMap);

  return useMemo(() => {
    // While modifying, show the element states and the pending modification
    // badges; the live execution overlays (waiting/agent) are hidden.
    if (isModificationModeEnabled) {
      return [...elementStateData, ...modificationBadgeData];
    }

    return [
      ...elementStateData,
      ...waitingStateData,
      ...agentStatusData,
      ...agentShineData,
    ];
  }, [
    isModificationModeEnabled,
    elementStateData,
    modificationBadgeData,
    waitingStateData,
    agentStatusData,
    agentShineData,
  ]);
};

export {useDiagramOverlaysData};
