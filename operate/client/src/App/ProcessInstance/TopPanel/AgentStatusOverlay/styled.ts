/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled, {keyframes} from 'styled-components';

// NOTE: These colors and styles are custom built and only loosely follow Carbon.
// Gradients combined with some animations are supposed to create a "shiny" AI effect.

// "AI" colors based on Carbon's non-exposed purple tokens, and Camunda's brand purple.
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

export {
  AgentStatusContainer,
  AgentStatus,
  PURPLE_20,
  PURPLE_30,
  PURPLE_40,
  PURPLE_60,
};
