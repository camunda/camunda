/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';

import {Colors, themed, themeStyle} from 'modules/theme';

const HEIGHT = 4;

export const Container = styled.div`
  position: relative;
  height: ${HEIGHT}px;
  width: 100%;
`;

export const Background = themed(styled.div`
  background-color: ${Colors.selections};
  opacity: ${themeStyle({
    dark: '0.2',
    light: '0.3',
  })};
  position: absolute;
  height: ${HEIGHT}px;
  width: 100%;
`);

export const Bar = styled.div`
  background-color: ${Colors.selections};
  position: absolute;
  height: ${HEIGHT}px;
  width: ${({width}) => width}%;
`;
