/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/* eslint-disable react-refresh/only-export-components -- overlay modules intentionally co-locate their data hook, config, and renderer in a single file */

import {useMemo} from 'react';
import {
  WAITING_BADGE,
  WAITING_BADGE_NARROW,
} from 'modules/bpmn-js/badgePositions';
import {WaitingStateOverlay as WaitingState} from 'modules/components/WaitingStateOverlay';
import {useProcessInstance} from 'modules/queries/processInstance/useProcessInstance';
import {
  MAX_WAIT_STATES,
  useElementInstanceInspection,
} from 'modules/queries/elementInstanceInspection/useElementInstanceInspection';
import {getWaitStateLabel} from 'modules/utils/waitStates';
import {getClientConfig} from 'modules/utils/getClientConfig';
import {useProcessInstancePageParams} from '../../useProcessInstancePageParams';
import type {OverlayData} from 'modules/bpmn-js/overlayTypes';
import type {DiagramOverlay} from './types';

const WAITING_STATE_OVERLAY_TYPE = 'waitingState';

// Gateways and events are narrow (~36px) symbols.
const NARROW_WAIT_STATE_ELEMENT_TYPES = new Set<string>([
  'EXCLUSIVE_GATEWAY',
  'PARALLEL_GATEWAY',
  'INCLUSIVE_GATEWAY',
  'EVENT_BASED_GATEWAY',
  'START_EVENT',
  'END_EVENT',
  'INTERMEDIATE_CATCH_EVENT',
  'INTERMEDIATE_THROW_EVENT',
  'BOUNDARY_EVENT',
]);

type WaitingStatePayload = {
  label: string;
  centered: boolean;
};

/**
 * Builds the waiting-state overlays. The set of elements that currently have an
 * agent instance is passed in so the waiting state can hide itself wherever an
 * agent overlay is shown — letting both overlays share a single agent-instance
 * subscription instead of each computing it on their own.
 */
const useWaitingStateOverlaysData = (
  elementsWithAgent: Set<string>,
): OverlayData[] => {
  const clientConfig = getClientConfig();
  const {processInstanceId = ''} = useProcessInstancePageParams();
  const {data: processInstance} = useProcessInstance();
  const {data: inspectionData} = useElementInstanceInspection({
    processInstanceKey: processInstanceId,
    enabled:
      clientConfig.waitStatesEnabled && processInstance?.state === 'ACTIVE',
  });

  return useMemo(() => {
    if (!inspectionData?.items?.length) {
      return [];
    }

    // Group wait states by elementId (show only 1 label per element)
    const waitStatesByElement = new Map<string, typeof inspectionData.items>();
    for (const item of inspectionData.items) {
      const existing = waitStatesByElement.get(item.elementId) ?? [];
      existing.push(item);
      waitStatesByElement.set(item.elementId, existing);
    }

    const overlays: OverlayData[] = [];
    const hasMore = (inspectionData.page?.totalItems ?? 0) > MAX_WAIT_STATES;

    for (const [elementId, waitStates] of waitStatesByElement) {
      // Hide the waiting state when an agent instance exists for the element.
      if (elementsWithAgent.has(elementId)) {
        continue;
      }
      const label = getWaitStateLabel(waitStates, hasMore);
      if (label) {
        const isNarrowElement = waitStates.some((waitState) =>
          NARROW_WAIT_STATE_ELEMENT_TYPES.has(waitState.elementType),
        );
        overlays.push({
          elementId,
          type: WAITING_STATE_OVERLAY_TYPE,
          position: isNarrowElement ? WAITING_BADGE_NARROW : WAITING_BADGE,
          payload: {
            label,
            centered: isNarrowElement,
          } satisfies WaitingStatePayload,
        });
      }
    }

    return overlays;
  }, [inspectionData, elementsWithAgent]);
};

const WaitingStateOverlay: React.FC<{overlay: DiagramOverlay}> = ({
  overlay,
}) => {
  const payload = overlay.payload as WaitingStatePayload;

  return (
    <WaitingState
      container={overlay.container}
      label={payload.label}
      centered={payload.centered}
    />
  );
};

const getWaitingStateOverlayKey = (overlay: DiagramOverlay): string =>
  `waiting-${overlay.elementId}`;

export {
  WAITING_STATE_OVERLAY_TYPE,
  useWaitingStateOverlaysData,
  WaitingStateOverlay,
  getWaitingStateOverlayKey,
};
