/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/* eslint-disable react-refresh/only-export-components -- overlay modules intentionally co-locate their data hook, config, and renderer in a single file */

import {useLayoutEffect, useMemo, useState} from 'react';
import {createPortal} from 'react-dom';
import styled, {keyframes} from 'styled-components';
import {AGENT_SHINE} from 'modules/bpmn-js/badgePositions';
import {PURPLE_30, PURPLE_40, PURPLE_60} from './agentStatusOverlay';
import type {
  AgentShinePayload,
  OverlayData,
} from 'modules/bpmn-js/overlayTypes';
import type {DiagramOverlay} from './types';
import type {AgentInstancesStatusMap} from './agentInstances';

const AGENT_SHINE_OVERLAY_TYPE = 'agentShine';

// NOTE: Custom colors and styles that create a shimmering outline that sits above
// the overlaid element. The goal is a "shiny" AI effect.

const shine = keyframes`
  0% { background-position: 0% 0%; }
  50% { background-position: 100% 100%; }
  100% { background-position: 0% 0%; }
`;

const mask = `linear-gradient(#fff 0 0) content-box, linear-gradient(#fff 0 0)`;

const ShineBox = styled.div<{
  $width: number;
  $height: number;
  $radius: number;
}>`
  position: absolute;
  /* These tiny adjustments compared to the overlaid element ensure that the outlines overlap perfectly. */
  inset: -1px;
  width: ${({$width}) => $width + 2}px;
  height: ${({$height}) => $height + 2}px;
  border-radius: ${({$radius}) => $radius + 1}px;
  pointer-events: none;
  z-index: -1;

  padding: var(--cds-spacing-01);
  will-change: background-position;
  animation: ${shine} 8s linear infinite;
  background-size: 300% 300%;
  background-image: radial-gradient(
    transparent,
    transparent,
    ${PURPLE_40},
    ${PURPLE_60},
    ${PURPLE_30},
    transparent,
    transparent
  );
  mask: ${mask};
  -webkit-mask: ${mask};
  -webkit-mask-composite: xor;
  mask-composite: exclude;
`;

type Size = {
  width: number;
  height: number;
  radius: number;
};

const useAgentShineOverlaysData = (
  agentInstancesStatusMap: AgentInstancesStatusMap,
): OverlayData[] =>
  useMemo(
    () =>
      Array.from(
        agentInstancesStatusMap.entries(),
        ([elementId, statusInfo]) => ({
          type: AGENT_SHINE_OVERLAY_TYPE,
          elementId,
          position: AGENT_SHINE,
          payload: {
            agentInstanceKey: statusInfo.agentInstanceKey,
          } satisfies AgentShinePayload,
        }),
      ),
    [agentInstancesStatusMap],
  );

const AgentShineOverlay: React.FC<{overlay: DiagramOverlay}> = ({overlay}) => {
  const {elementId, container} = overlay;
  const [size, setSize] = useState<Size | null>(null);

  useLayoutEffect(() => {
    const hostElement = document.querySelector<SVGGraphicsElement>(
      `[data-element-id="${elementId}"] .djs-visual rect`,
    );
    if (hostElement === null) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setSize(null);
      return;
    }

    const width = Number.parseFloat(hostElement.getAttribute('width') ?? '0');
    const height = Number.parseFloat(hostElement.getAttribute('height') ?? '0');
    const radius = Number.parseFloat(hostElement.getAttribute('rx') ?? '0');
    setSize({
      width: Number.isFinite(width) ? width : 0,
      height: Number.isFinite(height) ? height : 0,
      radius: Number.isFinite(radius) ? radius : 0,
    });
  }, [elementId]);

  if (size === null) {
    return null;
  }

  return createPortal(
    <ShineBox
      data-testid={`agent-shine-overlay-${elementId}`}
      $width={size.width}
      $height={size.height}
      $radius={size.radius}
    />,
    container,
  );
};

const getAgentShineOverlayKey = (overlay: DiagramOverlay): string =>
  `${(overlay.payload as AgentShinePayload).agentInstanceKey}-shine`;

export {
  AGENT_SHINE_OVERLAY_TYPE,
  useAgentShineOverlaysData,
  AgentShineOverlay,
  getAgentShineOverlayKey,
};
