/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';
import withStrippedProps from 'modules/utils/withStrippedProps';

export const Wrapper = themed(styled(withStrippedProps(['perUnit'])('div'))`
  display: flex;
  padding: 0;

  color: ${({perUnit}) => {
    return !perUnit
      ? Colors.incidentsAndErrors
      : themeStyle({
          dark: 'rgba(255, 255, 255, 0.9)',
          light: Colors.uiLight06
        });
  }};

  font-family: IBMPlexSans;
  font-size: ${({perUnit}) => {
    return perUnit ? '13px' : '14px';
  }};
  font-weight: ${({perUnit}) => {
    return perUnit ? '400' : '600';
  }};
  line-height: 1.71;
`);

export const IncidentsCount = styled.div`
  width: 96px;
  color: ${Colors.incidentsAndErrors};
`;

export const Label = styled.div`
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
`;

export const IncidentBar = themed(styled.div`
  display: flex;
  height: 2px;
  align-items: stretch;
  background: ${themeStyle({
    dark: Colors.uiDark05,
    light: Colors.uiLight05
  })};
`);
