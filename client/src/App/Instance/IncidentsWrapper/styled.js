/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';

import {Transition as TransitionComponent} from 'modules/components/Transition';

const borderColor = themeStyle({
  dark: Colors.uiDark04,
  light: Colors.uiLight05
});

export const Transition = themed(styled(TransitionComponent)`
  &.transition-enter {
    border-bottom: 1px solid ${borderColor};
    top: -100%;
  }
  &.transition-enter-active {
    border-bottom: 1px solid ${borderColor};
    top: 0%;
    transition: top ${({timeout}) => timeout + 'ms'};
  }
  &.transition-exit {
    top: 0%;
    border: none;
  }
  &.transition-exit-active {
    top: -100%;
    border-bottom: 1px solid ${borderColor};
    transition: top ${({timeout}) => timeout + 'ms'};
  }
`);
