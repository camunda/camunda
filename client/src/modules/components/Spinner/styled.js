/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {keyframes} from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';

const SpinnerKeyframe = keyframes`
  0% {
    transform: rotate(0deg);
  }
  100% {
    transform: rotate(360deg);
  }
`;

export const Spinner = themed(styled.div`
  border-radius: 50%;

  // intentionally sized based on the parent's font-size
  width: 1em;
  height: 1em;

  position: relative;

  border: 3px solid
    ${themeStyle({
      dark: '#ffffff',
      light: Colors.badge02
    })};
  border-right-color: transparent;

  animation: ${SpinnerKeyframe} 0.65s infinite linear;
`);
