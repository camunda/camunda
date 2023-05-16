/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {Transition as TransitionComponent} from 'modules/components/Transition';
import {PanelHeader as BasePanelHeader} from 'modules/components/Carbon/PanelHeader';

type TransitionProps = {
  timeout: number;
};

const Transition = styled(TransitionComponent)<TransitionProps>`
  ${({timeout}) => {
    return css`
      &.transition-enter {
        border-bottom: 1px solid var(--cds-border-subtle-01);
        top: -100%;
      }
      &.transition-enter-active {
        border-bottom: 1px solid var(--cds-border-subtle-01);
        top: 0%;
        transition: top ${timeout}ms;
      }
      &.transition-exit {
        top: 0%;
        border: none;
      }
      &.transition-exit-active {
        top: -100%;
        border-bottom: 1px solid var(--cds-border-subtle-01);
        transition: top ${timeout}ms;
      }
    `;
  }}
`;

const PanelHeader = styled(BasePanelHeader)`
  min-height: var(--cds-spacing-08);
  height: var(--cds-spacing-08);
`;

export {Transition, PanelHeader};
