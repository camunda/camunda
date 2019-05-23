/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';
import {Link} from 'react-router-dom';

export const Li = styled.li`
  margin: 0 10px 6px 0;
`;
export const VersionLi = styled.li`
  margin: 2px 0 0;
  padding: 0;
`;

export const IncidentLink = themed(styled(Link)`
  display: block;
  padding: 10px 8px;
  margin-left: 24px;
  border: 1px solid transparent;

  &:hover {
    box-shadow: ${themeStyle({
      dark: '0 0 4px 0 #000000',
      light: '0 0 5px 0 rgba(0, 0, 0, 0.1)'
    })};
    border-color: ${themeStyle({
      dark: Colors.uiDark05,
      light: 'rgba(216, 220, 227, 0.5)'
    })};
  }

  &:active {
    box-shadow: ${themeStyle({
      dark: 'inset 0 0 6px 0 rgba(0, 0, 0, 0.4)',
      light: 'inset 0 0 6px 0 rgba(0, 0, 0, 0.1)'
    })};
    border-color: ${themeStyle({
      dark: 'rgba(91, 94, 99, 0.7)',
      light: 'rgba(216, 220, 227, 0.4)'
    })};
  }
`);
