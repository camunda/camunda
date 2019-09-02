/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';

import {themed, themeStyle, Colors} from 'modules/theme';
import {ReactComponent as UpBar} from 'modules/components/Icon/up-bar.svg';
import {ReactComponent as DownBar} from 'modules/components/Icon/down-bar.svg';
import {ReactComponent as LeftBar} from 'modules/components/Icon/left-bar.svg';
import {ReactComponent as RightBar} from 'modules/components/Icon/right-bar.svg';

export const CollapseButton = themed(styled.button`
  margin: 3px;
  display: flex;
  justify-content: center;
  align-items: center;

  width: 39px;
  height: 32px;

  background: transparent;

  border: solid 1px
    ${themeStyle({
      dark: Colors.uiDark04,
      light: Colors.uiLight05
    })};
`);

const iconStyle = css`
  width: 16px;
  height: 16px;
  object-fit: contain;
  opacity: ${themeStyle({
    dark: 0.5,
    light: 0.9
  })};
  color: ${themeStyle({
    dark: '#ffffff',
    light: Colors.uiDark06
  })};

  &:hover {
    opacity: ${themeStyle({
      dark: 0.7,
      light: 1
    })};
  }

  &:active {
    opacity: ${themeStyle({
      dark: 1,
      light: 1
    })};
    color: ${themeStyle({
      dark: 0.5,
      light: Colors.uiDark04
    })};
  }
`;

export const Up = themed(styled(UpBar)`
  ${iconStyle};
`);

export const Down = themed(styled(DownBar)`
  ${iconStyle};
`);

export const Left = themed(styled(LeftBar)`
  ${iconStyle};
`);

export const Right = themed(styled(RightBar)`
  ${iconStyle};
`);
