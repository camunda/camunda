/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';

import {ReactComponent as Right} from 'modules/components/Icon/right.svg';
import {Transition as TransitionComponent} from 'modules/components/Transition';

const ArrowIcon = styled(Right)`
  width: 16px;
  height: 16px;
  object-fit: contain;
`;

const Transition = styled(TransitionComponent)`
  ${({timeout}) => {
    return css`
      &.transition-enter {
        transform: none;
      }

      &.transition-enter-active {
        transform: rotate(90deg);
        transition: transform ${timeout}ms;
      }

      &.transition-enter-done {
        transform: rotate(90deg);
      }

      &.transition-exit {
        transform: rotate(90deg);
      }

      &.transition-exit-active {
        transform: none;
        transition: transform ${timeout}ms;
      }

      &.transition-exit-done {
        transform: none;
      }

      &.transition-appear {
        transform: rotate(90deg);
      }
    `;
  }}
`;

export {ArrowIcon, Transition};
