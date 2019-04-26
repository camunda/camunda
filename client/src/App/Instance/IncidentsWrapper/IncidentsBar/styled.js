/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';
import {INCIDENTS_BAR_HEIGHT} from 'modules/constants.js';
import {Colors, themed} from 'modules/theme';
import withStrippedProps from 'modules/utils/withStrippedProps';
import {ReactComponent as Down} from 'modules/components/Icon/down.svg';
import {Transition as TransitionComponent} from 'modules/components/Transition';

export const IncidentsBar = styled.button`
  display: flex;
  align-items: center;
  position: relative;
  z-index: 4;

  height: ${INCIDENTS_BAR_HEIGHT}px;
  padding: 0 20px 0 17px;
  font-size: 15px;
  font-weight: bold;
  opacity: 1;

  background-color: ${Colors.incidentsAndErrors};
  color: #ffffff;

  cursor: pointer;
`;

export const Transition = themed(styled(TransitionComponent)`
  &.transition-enter {
    transform: rotate(-90deg);
  }
  &.transition-enter-active {
    transform: none;
    transition: transform ${({timeout}) => timeout + 'ms'};
  }

  &.transition-enter-done {
    transform: none;
  }

  &.transition-exit {
    transform: none;
  }
  &.transition-exit-active {
    transform: rotate(-90deg);
    transition: transform ${({timeout}) => timeout + 'ms'};
  }
`);

export const Arrow = styled(withStrippedProps(['isFlipped'])(Down))`
  margin-right: 11px;
  position: relative;
  transform: rotate(-90deg);
`;
