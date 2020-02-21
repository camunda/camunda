/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';

const runningEntryStyles = css`
  background-color: ${themeStyle({
    dark: Colors.uiDark03,
    light: Colors.uiLight04
  })};
`;

const finishedEntryStyles = css`
  background-color: ${themeStyle({
    dark: Colors.uiDark02,
    light: Colors.uiLight02
  })};
`;

export const Entry = themed(styled.li`
  color: ${themeStyle({
    dark: 'rgba(255, 255, 255, 0.9)',
    light: 'rgba(98, 98, 110, 0.9)'
  })};

  ${({isRunning}) => (isRunning ? runningEntryStyles : finishedEntryStyles)}

  border-top: ${themeStyle({
    dark: `solid 1px ${Colors.uiDark04}`,
    light: `solid 1px ${Colors.uiLight05}`
  })};
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  min-height: 130px;
  padding: 17px 27px;
`);

export const Type = styled.div`
  font-size: 15px;
  line-height: 19px;
  font-weight: 600;

  margin-bottom: 6px;
`;

export const Id = themed(styled.div`
  font-size: 11px;

  color: ${themeStyle({
    dark: 'rgba(255, 255, 255, 0.9)',
    light: Colors.uiLight06
  })};
`);
