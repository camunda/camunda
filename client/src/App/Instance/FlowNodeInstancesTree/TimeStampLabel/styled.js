/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';

export const TimeStamp = themed(styled.span`
  margin-left: 14px;
  padding: 2px 4px;
  color: ${themeStyle({
    dark: '#fff',
    light: Colors.uiLight06,
  })};
  background: ${({isSelected}) =>
    isSelected
      ? themeStyle({
          dark: 'rgba(247, 248, 250, 0.15)',
          light: 'rgba(253, 253, 254, 0.55)',
        })
      : themeStyle({
          dark: Colors.darkScopeLabel,
          light: Colors.lightScopeLabel,
        })};

  font-size: 11px;
  border-radius: 2px;
`);
