/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo} from 'react';
import type {OverlayData} from 'modules/bpmn-js/overlayTypes';
import {useFirstAgentInstancePerElement} from './agentInstances';
import {useElementStateOverlaysData} from './elementStateOverlay';
import {useModificationBadgeOverlaysData} from './modificationBadgeOverlay';
import {useWaitingStateOverlaysData} from './waitingStateOverlay';
import {useAgentStatusOverlaysData} from './agentStatusOverlay';
import {useAgentShineOverlaysData} from './agentShineOverlay';

/**
 * Builds the combined overlay data for the diagram by wiring each overlay's data
 * hook together. The result is passed to `<Diagram overlaysData={...} />`.
 *
 * Each overlay hook is called unconditionally to respect the rules of hooks; this
 * hook then decides which overlays to expose based on the diagram mode. Sharing
 * `useFirstAgentInstancePerElement` here lets the agent overlays and the waiting
 * state overlay coordinate through a single agent-instance subscription.
 *
 * To add a new overlay type: create a `useXOverlaysData` hook and an `XOverlay`
 * renderer (see the existing overlay files), call the hook here, and render the
 * overlay in `./index.tsx`.
 */
const useDiagramOverlaysData = (
  isModificationModeEnabled: boolean,
): OverlayData[] => {
  const {agentInstances, elementsWithAgent} = useFirstAgentInstancePerElement();

  const elementStateData = useElementStateOverlaysData();
  const modificationBadgeData = useModificationBadgeOverlaysData();
  const waitingStateData = useWaitingStateOverlaysData(elementsWithAgent);
  const agentStatusData = useAgentStatusOverlaysData(agentInstances);
  const agentShineData = useAgentShineOverlaysData(agentInstances);

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
