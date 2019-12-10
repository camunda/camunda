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
`);

export const Row = themed(styled.div`
  display: flex;
  max-width: 300px;
  padding: 8px 10px;
`);

export const Block = themed(styled.div`
  margin-left: ${({parent}) => (!!parent ? '20px' : '52px')};
  height: 12px;
  flex-grow: 1;
  ${colors};
`);

export const Circle = themed(styled.div`
  border-radius: 50%;
  height: 12px;
  width: 12px;

  ${colors};
`);
