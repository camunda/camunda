/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled, {keyframes} from 'styled-components';
import {PURPLE_30, PURPLE_40, PURPLE_60} from '../AgentStatusOverlay/styled';

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

export {ShineBox};
