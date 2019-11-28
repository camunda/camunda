/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';

export const Footer = themed(styled.div`
  z-index: 1;
  height: 38px;
  width: 100%;

  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding-right: 20px;

  border-top: solid 1px
    ${themeStyle({
      dark: Colors.uiDark04,
      light: Colors.uiLight05
    })};
  background-color: ${themeStyle({
    dark: Colors.uiDark03,
    light: Colors.uiLight02
  })};
  color: ${themeStyle({
    dark: '#ffffff',
    light: Colors.uiLight06
  })};
`);
