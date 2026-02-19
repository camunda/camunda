/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {OverlayPosition} from 'bpmn-js/lib/NavigatedViewer';
import type {FlowNodeState} from 'modules/types/operate';

type OverlayData = {
  payload?: unknown;
  type: string;
  flowNodeId: string;
  position: OverlayPosition;
  isZoomFixed?: boolean;
};

type StatisticsPayload = {
  flowNodeState: FlowNodeState;
  count: number;
};

type ModificationBadgePayload = {
  newTokenCount?: number;
  cancelledTokenCount?: number;
};

const isStatisticsPayload = (
  payload: unknown,
): payload is StatisticsPayload => {
  return (
    typeof payload === 'object' &&
    payload !== null &&
    'flowNodeState' in payload &&
    'count' in payload
  );
};

const isModificationBadgePayload = (
  payload: unknown,
): payload is ModificationBadgePayload => {
  return (
    typeof payload === 'object' &&
    payload !== null &&
    ('newTokenCount' in payload || 'cancelledTokenCount' in payload)
  );
};

export {isStatisticsPayload, isModificationBadgePayload};
export type {OverlayData};
