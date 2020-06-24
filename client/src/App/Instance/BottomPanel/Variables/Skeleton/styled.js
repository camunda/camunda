/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';
import {Table as DefaultTable} from '../../VariablesTable';

const SkeletonTD = styled.td`
  height: 100%;
  padding-top: 44px;
`;

const Table = styled(DefaultTable)`
  height: 100%;
  width: 100%;
  margin-bottom: 3px;
  border-spacing: 0;
  border-collapse: collapse;
`;

const THead = themed(styled.thead`
  tr:first-child {
    position: absolute;
    width: 100%;
    top: 0;

    border-bottom: 1px solid
      ${themeStyle({
        dark: Colors.uiDark04,
        light: Colors.uiLight05,
      })};
    background: ${themeStyle({
      dark: Colors.uiDark02,
      light: Colors.uiLight04,
    })};
    z-index: 2;
    border-top: none;
    height: 45px;
    border-top: none;
    > th {
      padding-top: 21px;
    }
    > th:first-child {
      min-width: 226px;
    }
  }
`);

export {SkeletonTD, Table, THead};
