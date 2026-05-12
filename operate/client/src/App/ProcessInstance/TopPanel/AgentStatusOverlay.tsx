/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Tag} from '@carbon/react';
import {styles} from '@carbon/elements';
import {createPortal} from 'react-dom';
import styled, {keyframes} from 'styled-components';

// Carbon `--cds-purple-*` scale tokens aren't exposed as CSS variables by default,
// so the AI-themed purple shades are inlined here (matches ShineBorderOverlay).
const PURPLE_20 = '#e8daff';
const PURPLE_30 = '#d4bbff'; // shine highlight — brighter glint, ~35pt lightness jump from purple-60
const PURPLE_60 = '#8a3ffc'; // resting text — Camunda's brand purple

// Continuous left-to-right text shine: the gradient packs two highlights spaced
// one element-width apart (image positions 25% and 75% over a 200% bg-size), so
// as one highlight exits the right edge the next enters from the left. The two
// highlights are visually identical, so the loop point — where the bg-position
// resets — is invisible. No pause between cycles.
const textShine = keyframes`
  0% { background-position: 100% 50%; }
  100% { background-position: 0% 50%; }
`;

const AgentTag = styled(Tag)`
  &.cds--tag {
    background-color: #ffffff;
    border: none;
    border-radius: 12px;
    box-shadow: none;
    /* Two stacked drop-shadows */
    filter: drop-shadow(0 0 0.5px ${PURPLE_20})
      drop-shadow(0 0 4px ${PURPLE_20});
    padding: 3px 8px;
    margin: 0;
    min-height: auto;
  }

  &.cds--tag .cds--tag__label,
  &.cds--tag span {
    ${styles.label01};
    font-weight: 600;
    font-style: normal;
    white-space: nowrap;

    /* Text-shine: letters rest at purple-60 (brand) with a purple-30 glint
       sweeping across. Two highlights packed one element-width apart give a
       continuous flow — no pause between cycles. Gradient is clipped to the
       glyphs via background-clip: text. Tuned for the light canvas; if Operate
       ever ships a dark-canvas mode, invert (resting purple-30, shine white)
       inside a prefers-color-scheme / Carbon theme selector. */
    background-image: linear-gradient(
      110deg,
      ${PURPLE_60} 0%,
      ${PURPLE_30} 25%,
      ${PURPLE_60} 50%,
      ${PURPLE_30} 75%,
      ${PURPLE_60} 100%
    );
    background-size: 200% 100%;
    background-position: 100% 50%;
    background-clip: text;
    -webkit-background-clip: text;
    color: transparent;
    -webkit-text-fill-color: transparent;
    /* Adjust speed here: change "5s" (higher = slower). */
    animation: ${textShine} 2s linear infinite;
  }
`;

type Props = {
  container: HTMLElement;
  label: string;
};

const AgentStatusOverlay: React.FC<Props> = ({container, label}) => {
  return createPortal(<AgentTag size="md">{label}</AgentTag>, container);
};

export {AgentStatusOverlay};
