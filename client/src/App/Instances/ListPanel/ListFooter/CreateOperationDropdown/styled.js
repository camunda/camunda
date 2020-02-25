/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
import {Colors} from 'modules/theme';

const MENU_WIDTH = 165;
const MENU_RIGHT_OFFSET = 43;

export const DropdownContainer = styled.div`
  position: relative;
  width: fit-content;

  ul {
    min-width: ${MENU_WIDTH}px;
    position: absolute;
    bottom: 30px;
    right: calc(${MENU_RIGHT_OFFSET}px - ${MENU_WIDTH}px);
  }
`;

export const dropdownButtonStyles = css`
  font-size: 13px;
  font-weight: 600;

  background-color: ${Colors.selections};
  color: ${Colors.uiLight04}

  &:hover {
    background-color: ${Colors.primaryButton04};
  }

  &:active,
  &[data-button-open=true] {
    background-color: #1a70ff;
  }

  height: 26px;
  border-radius: 13px;
  border: none;
  padding: 4px 11px 5px 11px;
  
`;
