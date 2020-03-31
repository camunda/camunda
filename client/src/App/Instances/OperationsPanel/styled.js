/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';

export const OperationsList = styled.ul`
  overflow-y: auto;
  li:first-child {
    border-top: none;
  }
`;

export const EmptyMessage = themed(styled.div`
  border: 1px solid
    ${themeStyle({
      dark: Colors.uiDark04,
      light: Colors.uiLight05,
    })};
  border-radius: 3px;
  margin: 30px 17px 0 18px;
  padding: 29px 44px 29px 32px;
  text-align: center;
  font-size: 13px;
  font-family: 'IBMPlexSans';
  color: ${themeStyle({
    dark: 'rgba(255, 255, 255, 0.9)',
    light: Colors.uiLight06,
  })};
  background-color: ${themeStyle({
    dark: Colors.uiDark03,
    light: Colors.uiLight04,
  })};
`);
