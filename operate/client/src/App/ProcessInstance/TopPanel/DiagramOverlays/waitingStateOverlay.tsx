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
import {useWaitStateStatistics} from 'modules/queries/waitStateStatistics/useWaitStateStatistics';
import {useBusinessObjects} from 'modules/queries/processDefinitions/useBusinessObjects';
import {getWaitStateLabel} from 'modules/utils/waitStates';
import {getClientConfig} from 'modules/utils/getClientConfig';
import type {OverlayData} from 'modules/bpmn-js/overlayTypes';
import type {DiagramOverlay} from './types';

const WAITING_STATE_OVERLAY_TYPE = 'waitingState';

// Gateways and events are narrow (~36px) symbols.
const NARROW_WAIT_STATE_BPMN_TYPES = new Set<string>([
  'bpmn:ExclusiveGateway',
  'bpmn:ParallelGateway',
  'bpmn:InclusiveGateway',
  'bpmn:EventBasedGateway',
  'bpmn:ComplexGateway',
  'bpmn:StartEvent',
  'bpmn:EndEvent',
  'bpmn:IntermediateCatchEvent',
  'bpmn:IntermediateThrowEvent',
  'bpmn:BoundaryEvent',
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
  const {data: waitStateStatistics} = useWaitStateStatistics({
    enabled: clientConfig.waitStatesEnabled,
  });
  const {data: businessObjects} = useBusinessObjects();

  return useMemo(() => {
    if (!waitStateStatistics?.length) {
      return [];
    }

    const overlays: OverlayData[] = [];

    for (const {elementId, waitingCount} of waitStateStatistics) {
      // Hide the waiting state when an agent instance exists for the element.
      if (elementsWithAgent.has(elementId)) {
        continue;
      }
      const label = getWaitStateLabel(waitingCount);
      if (label) {
        const isNarrowElement = NARROW_WAIT_STATE_BPMN_TYPES.has(
          businessObjects?.[elementId]?.$type ?? '',
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
  }, [waitStateStatistics, businessObjects, elementsWithAgent]);
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
