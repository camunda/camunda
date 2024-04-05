/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {TransitionGroup as BaseTransitionGroup} from 'react-transition-group';

type Props = {
  $animationTimeout: number;
};

const TransitionGroup = styled(BaseTransitionGroup)<Props>`
  ${({$animationTimeout}) => css`
    .toast-enter {
      transform: translateX(120%);
    }

    .toast-enter-active {
      transform: translateX(0);
      transition: transform ${$animationTimeout}ms ease-in-out;
    }

    .toast-exit-active {
      transform: translateX(120%);
      transition: transform ${$animationTimeout}ms ease-in-out;
    }

    .toast-exit-done {
      display: none;
    }
  `}
`;

export {TransitionGroup};
