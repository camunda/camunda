/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';

export const EmptyMessage = themed(styled.div`
  padding-top: 40px;

  font-family: IBMPlexSans;
  font-size: 16px;
  font-weight: 500;
  text-align: center;
  line-height: 20px;

  color: ${themeStyle({
    dark: Colors.darkDiagram,
    light: Colors.uiLight06,
  })};

  span {
    display: block;
  }
`);
