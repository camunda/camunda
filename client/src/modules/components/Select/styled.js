/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';

const disabledStyleDark = css`
  background-color: rgba(62, 63, 69, 0.4);
  border-color: rgba(91, 94, 99, 0.2);
  color: rgba(255, 255, 255, 0.5);
`;

const disabledStyleLight = css`
  background-color: rgba(242, 243, 245, 0.4);
  border: solid 1px rgba(176, 186, 199, 0.2);
  color: rgba(98, 98, 110, 0.7);
`;

export const Select = themed(styled.select`
  width: 100%;
  height: 26px;

  border: solid 1px
    ${themeStyle({
      dark: Colors.uiDark05,
      light: Colors.uiLight05,
    })};
  border-radius: 3px;

  background-color: ${themeStyle({
    dark: '#3e3f45',
    light: Colors.uiLight01,
  })};
  color: ${themeStyle({
    dark: '#ffffff',
    light: Colors.uiDark06,
  })};

  font-family: IBMPlexSans;
  font-size: 13px;

  box-shadow: ${({theme}) =>
    theme === 'dark'
      ? '0 2px 2px 0 rgba(0, 0, 0, 0.35)'
      : '0 2px 2px 0 rgba(0, 0, 0, 0.08)'};

  &:disabled {
    ${({theme}) => (theme === 'dark' ? disabledStyleDark : disabledStyleLight)};

    box-shadow: none;
    cursor: not-allowed;
  }

  /* removes default dotted-line-focus in firefox*/
  &:-moz-focusring {
    color: transparent;
    text-shadow: 0 0 0
      ${themeStyle({
        dark: '#ffffff',
        light: Colors.uiDark06,
      })};
  }
`);
