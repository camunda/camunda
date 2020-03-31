/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';

export const OptionButton = themed(styled.button`
  /* Positioning */
  position: relative;
  height: 36px;

  /* Display & Box Model */
  display: flex;
  align-items: center;
  width: 100%;
  padding: 0 10px;
  border: none;

  /* Color */
  background: none;
  color: ${themeStyle({
    dark: 'rgba(255, 255, 255, 0.9)',
    light: 'rgba(98, 98, 110, 0.9)',
  })};
  /* Text */
  text-align: left;
  font-size: 15px;
  font-weight: 600;
  line-height: 36px;

  &:hover {
    background: ${themeStyle({
      dark: Colors.uiDark06,
      light: Colors.uiLight05,
    })};
  }

  &:active {
    background: ${themeStyle({
      dark: Colors.darkActive,
      light: Colors.lightActive,
    })};
  }
`);
