/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';
import {INCIDENTS_BAR_HEIGHT} from 'modules/constants.js';
import {Colors} from 'modules/theme';

import BasicExpandButton from 'modules/components/ExpandButton';

export const IncidentsBar = styled(BasicExpandButton)`
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

  > :first-child {
    margin-right: 11px;
    margin-bottom: 1px;
  }
`;

// export const Transition = themed(styled(TransitionComponent)`
//   &.transition-enter {
//     transform: rotate(-90deg);
//   }
//   &.transition-enter-active {
//     transform: none;
//     transition: transform ${({timeout}) => timeout + 'ms'};
//   }

//   &.transition-enter-done {
//     transform: none;
//   }

//   &.transition-exit {
//     transform: none;
//   }
//   &.transition-exit-active {
//     transform: rotate(-90deg);
//     transition: transform ${({timeout}) => timeout + 'ms'};
//   }
// `);
