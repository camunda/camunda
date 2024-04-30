/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled, {css} from 'styled-components';
import {Transition as TransitionComponent} from 'modules/components/Transition';

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

export {Transition};
