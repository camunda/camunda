/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';
import BasicMultiRow from 'modules/components/MultiRow';

const colors = css`
  background: ${themeStyle({
    dark: 'rgba(136, 136, 141)',
    light: Colors.uiLight06
  })};
  opacity: ${themeStyle({
    dark: 0.2,
    light: 0.09
  })};
`;

export const MultiRow = themed(styled(BasicMultiRow)`
  width: 100%;
  overflow: hidden;
`);

export const Row = themed(styled.div`
  display: flex;

  border-top: 1px solid
    ${themeStyle({
      dark: Colors.uiDark04,
      light: Colors.uiLight05
    })};
  padding: 13px 10px;
`);

export const VariableBlock = themed(styled.div`
  margin-left: 4px;
  height: 14px;
  max-width: 200px;
  flex-grow: 1;
  ${colors};
`);

export const ValueBlock = themed(styled.div`
  margin-left: 30px;
  margin-right: 40px;
  height: 14px;

  flex-grow: 2;
  ${colors};
`);

export const Circle = themed(styled.div`
  border-radius: 50%;
  height: 12px;
  width: 12px;

  ${colors};
`);
