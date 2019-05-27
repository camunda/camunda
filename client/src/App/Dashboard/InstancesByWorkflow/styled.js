/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';
import {Link} from 'react-router-dom';
import withStrippedProps from 'modules/utils/withStrippedProps';

export const Li = styled.li`
  margin: 0 10px 11px 0;
`;

export const VersionList = styled.ul`
  margin-top: 6px;
  margin-bottom: 18px;
`;

export const VersionLi = styled.li`
  margin: 5px 0 0;
  padding: 0;
`;

export const IncidentLink = themed(
  styled(withStrippedProps(['boxSize'])(Link))`
    display: block;
    padding: ${props =>
      props.boxSize === 'small' ? '7px 8px 6px' : '8px 8px 7px'};
    margin-left: 24px;
    border: 1px solid transparent;
    border-radius: 3px;

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
  `
);
