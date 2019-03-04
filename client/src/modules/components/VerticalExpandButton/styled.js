/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';
import {themed, Colors, themeStyle} from 'modules/theme';

export const Button = themed(styled.button`
  height: 100%;
  width: 56px;
  padding: 11px;

  background: ${themeStyle({
    dark: Colors.uiDark03,
    light: Colors.uiLight02
  })};
  color: ${themeStyle({
    dark: '#fff',
    light: Colors.uiLight06
  })};
  border: none;

  opacity: 0.9;
  font-size: 15px;
  font-weight: bold;

  position: relative;
`);

export const Vertical = styled.span`
  position: absolute;
  top: 0;
  left: 0;
  transform: rotate(-90deg) translateX(-100%) translateY(100%);
  transform-origin: 0 0;
  display: flex;
  align-items: center;
  margin-top: 11px;
`;
