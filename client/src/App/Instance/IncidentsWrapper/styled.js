/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
import {Transition as TransitionComponent} from 'modules/components/Transition';

const Transition = styled(TransitionComponent)`
  ${({theme, timeout}) => {
    const colors = theme.colors.incidentsWrapper;

    return css`
      &.transition-enter {
        border-bottom: 1px solid ${colors.borderColor};
        top: -100%;
      }
      &.transition-enter-active {
        border-bottom: 1px solid ${colors.borderColor};
        top: 0%;
        transition: top ${timeout}ms;
      }
      &.transition-exit {
        top: 0%;
        border: none;
      }
      &.transition-exit-active {
        top: -100%;
        border-bottom: 1px solid ${colors.borderColor};
        transition: top ${timeout}ms;
      }
    `;
  }}
`;

export {Transition};
