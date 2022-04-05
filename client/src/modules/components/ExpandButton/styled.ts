/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';

import {ReactComponent as Right} from 'modules/components/Icon/right.svg';
import {Transition as TransitionComponent} from 'modules/components/Transition';

const ArrowIcon = styled(Right)`
  width: 16px;
  height: 16px;
  object-fit: contain;
`;

type TransitionProps = {
  timeout: number;
};

// @ts-expect-error ts-migrate(2769) FIXME: Type 'undefined' is not assignable to type 'ReactE... Remove this comment to see the full error message
const Transition = styled(TransitionComponent)<TransitionProps>`
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
