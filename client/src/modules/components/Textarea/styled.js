/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';

import BasicTextareaAutosize from 'react-textarea-autosize';

import {Colors, themed, themeStyle} from 'modules/theme';

const placeholderStyle = css`
  &::placeholder {
    color: ${themeStyle({
      light: 'rgba(98, 98, 110, 0.9)',
      dark: 'rgba(255, 255, 255, 0.7)'
    })};
    font-style: italic;
  }
`;

const TextareaStyles = css`
  display: block;

  width: 100%;
  height: 52px;
  padding: 6px 13px 4px 8px;

  border: solid 1px
    ${themeStyle({
      dark: Colors.uiDark05,
      light: Colors.uiLight05
    })};
  border-radius: 3px;

  background-color: ${themeStyle({
    dark: Colors.uiDark02,
    light: Colors.uiLight04
  })};
  color: ${themeStyle({
    dark: 'rgba(255, 255, 255, 0.9)',
    light: Colors.uiDark03
  })};

  font-family: IBMPlexSans;
  font-size: 13px;

  ${placeholderStyle};
`;

export const Textarea = themed(styled.textarea`
  ${TextareaStyles}
`);

export const TextareaAutosize = themed(styled(BasicTextareaAutosize)`
  ${TextareaStyles}
`);
