/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';

export const Header = themed(styled.div`
  background-color: ${themeStyle({
    dark: Colors.uiDark03,
    light: Colors.uiLight02
  })};
  color: ${themeStyle({
    dark: '#ffffff',
    light: Colors.uiLight06
  })};
  font-size: 15px;
  font-weight: bold;
  padding: 9px 10px;
  padding-left: 20px;
  height: 38px;

  display: flex;
  align-items: center;
`);

export const Headline = themed(styled.span``);
