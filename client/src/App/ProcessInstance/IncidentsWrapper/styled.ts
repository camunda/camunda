/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {Transition as TransitionComponent} from 'modules/components/Transition';

type TransitionProps = {
  timeout: number;
};

const Transition = styled(TransitionComponent)<TransitionProps>`
  ${({theme, timeout}) => {
    return css`
      &.transition-enter {
        border-bottom: 1px solid ${theme.colors.borderColor};
        top: -100%;
      }
      &.transition-enter-active {
        border-bottom: 1px solid ${theme.colors.borderColor};
        top: 0%;
        transition: top ${timeout}ms;
      }
      &.transition-exit {
        top: 0%;
        border: none;
      }
      &.transition-exit-active {
        top: -100%;
        border-bottom: 1px solid ${theme.colors.borderColor};
        transition: top ${timeout}ms;
      }
    `;
  }}
`;

export {Transition};
