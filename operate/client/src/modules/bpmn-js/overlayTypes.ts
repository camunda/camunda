/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {OverlayPosition} from 'bpmn-js/lib/NavigatedViewer';
import type {
  AgentInstance,
  ProcessDefinitionStatistic,
} from '@camunda/camunda-api-zod-schemas/8.10';

type ElementState = keyof Omit<ProcessDefinitionStatistic, 'elementId'>;

type OverlayData = {
  payload?: unknown;
  type: string;
  elementId: string;
  position: OverlayPosition;
  isZoomFixed?: boolean;
};

type StatisticsPayload = {
  elementState: ElementState;
  count: number;
};

type ModificationBadgePayload = {
  newTokenCount: number;
  cancelledTokenCount: number;
};

type WaitingStatePayload = {
  label: string;
};

type AgentStatusPayload = {
  status: AgentInstance['status'];
  agentInstanceKey: string;
};

type AgentShinePayload = {
  agentInstanceKey: string;
};

const OVERLAY_TYPE_STATE = 'elementState';
const OVERLAY_TYPE_MODIFICATIONS_BADGE = 'modificationsBadge';
const OVERLAY_TYPE_WAITING_STATE = 'waitingState';
const OVERLAY_TYPE_AGENT_STATUS = 'agentStatus';
const OVERLAY_TYPE_AGENT_SHINE = 'agentShine';

const isStatisticsPayload = (
  payload: unknown,
): payload is StatisticsPayload => {
  return (
    typeof payload === 'object' &&
    payload !== null &&
    'elementState' in payload &&
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

const isWaitingStatePayload = (
  payload: unknown,
): payload is WaitingStatePayload => {
  return typeof payload === 'object' && payload !== null && 'label' in payload;
};

const isAgentStatusPayload = (
  payload: unknown,
): payload is AgentStatusPayload => {
  return (
    typeof payload === 'object' &&
    payload !== null &&
    'status' in payload &&
    'agentInstanceKey' in payload
  );
};

const isAgentShinePayload = (
  payload: unknown,
): payload is AgentShinePayload => {
  return (
    typeof payload === 'object' &&
    payload !== null &&
    'agentInstanceKey' in payload &&
    !('status' in payload)
  );
};

export {
  OVERLAY_TYPE_STATE,
  OVERLAY_TYPE_MODIFICATIONS_BADGE,
  OVERLAY_TYPE_WAITING_STATE,
  OVERLAY_TYPE_AGENT_STATUS,
  OVERLAY_TYPE_AGENT_SHINE,
  isStatisticsPayload,
  isModificationBadgePayload,
  isWaitingStatePayload,
  isAgentStatusPayload,
  isAgentShinePayload,
};
export type {
  ElementState,
  OverlayData,
  ModificationBadgePayload,
  WaitingStatePayload,
  AgentStatusPayload,
  AgentShinePayload,
};
