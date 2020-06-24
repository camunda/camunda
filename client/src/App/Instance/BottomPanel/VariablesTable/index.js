/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';

const TH = themed(styled.th`
  font-style: italic;
  font-weight: normal;
  text-align: left;
  padding-left: 17px;
  height: 31px;
`);

const rowWithActiveOperationStyle = css`
  background-color: ${themeStyle({
    dark: 'rgba(91, 94, 99, 0.4)',
    light: '#e7e9ed',
  })};

  opacity: ${themeStyle({
    dark: '0.7',
  })};
`;

const TR = themed(styled.tr`
  border-width: 1px 0;
  border-style: solid;

  border-color: ${themeStyle({
    dark: Colors.uiDark04,
    light: Colors.uiLight05,
  })};

  &:first-child {
    border-top: none;
  }

  &:last-child {
    border-bottom: none;
  }

  > td:first-child {
    max-width: 226px;
    min-width: 226px;
    width: 226px;
  }

  ${({hasActiveOperation}) =>
    !hasActiveOperation ? '' : rowWithActiveOperationStyle};
`);

const Table = themed(styled.table`
  width: 100%;
  margin-bottom: 3px;
  border-spacing: 0;
  border-collapse: collapse;
`);

export {TH, TR, Table};
