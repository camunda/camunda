/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';

export const Panel = themed(styled.div`
  position: relative;
  display: flex;
  flex-direction: column;
  width: 100%;
  border: solid 1px
    ${themeStyle({
      dark: Colors.uiDark04,
      light: Colors.uiLight05,
    })};
  border-bottom: none;
  background-color: ${themeStyle({
    dark: Colors.uiDark02,
    light: Colors.uiLight04,
  })};
`);
