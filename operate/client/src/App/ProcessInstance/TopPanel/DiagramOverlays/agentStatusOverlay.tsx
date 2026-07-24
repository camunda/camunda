/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/* eslint-disable react-refresh/only-export-components -- overlay modules intentionally co-locate their data hook, config, and renderer in a single file */

import {useMemo} from 'react';
import {createPortal} from 'react-dom';
import styled, {keyframes} from 'styled-components';
import {AGENT_STATUS_TAG} from 'modules/bpmn-js/badgePositions';
import type {
  AgentInstance,
  AgentInstanceStatus,
} from '@camunda/camunda-api-zod-schemas/8.10';
import type {
  AgentStatusPayload,
  OverlayData,
} from 'modules/bpmn-js/overlayTypes';
import type {DiagramOverlay} from './types';

const AGENT_STATUS_OVERLAY_TYPE = 'agentStatus';

// NOTE: These colors and styles are custom built and only loosely follow Carbon.
// Gradients combined with some animations are supposed to create a "shiny" AI effect.

// "AI" colors based on Carbon's non-exposed purple tokens, and Camunda's brand
// purple. PURPLE_30/40/60 are shared with the agent shine overlay.
const PURPLE_20 = '#e8daff';
const PURPLE_30 = '#d4bbff';
const PURPLE_40 = '#a07cfe';
const PURPLE_60 = '#8a3ffc';

const AgentStatusContainer = styled.div`
  background-color: var(--color-white);
  padding: var(--cds-spacing-01) var(--cds-spacing-03);
  border-radius: 100px;
  filter: drop-shadow(0 0 0.5px ${PURPLE_20}) drop-shadow(0 0 4px ${PURPLE_20});
`;

const textShine = keyframes`
  0% { background-position: 100% 50%; }
  100% { background-position: 0% 50%; }
`;

const AgentStatus = styled.span`
  font-size: var(--cds-label-01-font-size);
  line-height: var(--cds-label-01-line-height);
  letter-spacing: var(--cds-label-01-letter-spacing);
  font-weight: 600;
  white-space: nowrap;
  background-image: linear-gradient(
    110deg,
    ${PURPLE_60} 0%,
    ${PURPLE_60} 18%,
    ${PURPLE_30} 25%,
    ${PURPLE_60} 32%,
    ${PURPLE_60} 68%,
    ${PURPLE_30} 75%,
    ${PURPLE_60} 82%,
    ${PURPLE_60} 100%
  );
  background-size: 200% 100%;
  background-position: 100% 50%;
  background-clip: text;
  -webkit-background-clip: text;
  color: transparent;
  -webkit-text-fill-color: transparent;
  animation: ${textShine} 2s linear infinite;
`;

const AGENT_STATUS_LABELS: Record<AgentInstanceStatus, string | null> = {
  UNKNOWN: null,
  INITIALIZING: 'Initializing...',
  TOOL_DISCOVERY: 'Discovering tools...',
  THINKING: 'Thinking...',
  TOOL_CALLING: 'Calling tools...',
  IDLE: null,
  COMPLETED: null,
};

const useAgentStatusOverlaysData = (
  agentInstances: AgentInstance[],
): OverlayData[] =>
  useMemo(
    () =>
      agentInstances.map((agentInstance) => ({
        type: AGENT_STATUS_OVERLAY_TYPE,
        elementId: agentInstance.elementId,
        position: AGENT_STATUS_TAG,
        payload: {
          status: agentInstance.status,
          agentInstanceKey: agentInstance.agentInstanceKey,
        } satisfies AgentStatusPayload,
      })),
    [agentInstances],
  );

const AgentStatusOverlay: React.FC<{overlay: DiagramOverlay}> = ({overlay}) => {
  const {status} = overlay.payload as AgentStatusPayload;
  const label = AGENT_STATUS_LABELS[status];
  if (label === null) {
    return null;
  }

  return createPortal(
    <AgentStatusContainer>
      <AgentStatus data-testid={`agent-status-overlay-${status}`}>
        {label}
      </AgentStatus>
    </AgentStatusContainer>,
    overlay.container,
  );
};

const getAgentStatusOverlayKey = (overlay: DiagramOverlay): string =>
  `${(overlay.payload as AgentStatusPayload).agentInstanceKey}-status`;

export {
  AGENT_STATUS_OVERLAY_TYPE,
  useAgentStatusOverlaysData,
  AgentStatusOverlay,
  getAgentStatusOverlayKey,
  PURPLE_30,
  PURPLE_40,
  PURPLE_60,
};
