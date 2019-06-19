/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';

const LinesSeparator = css`
  &:before {
    content: '';
    position: fixed;
    top: 55px;
    bottom: 0;
    left: 0;
    width: 32px;
    border-right: 1px solid
      ${themeStyle({
        dark: Colors.uiDark02,
        light: Colors.uiLight05
      })};

    background-color: ${themeStyle({
      dark: Colors.uiDark01,
      light: Colors.uiLight04
    })};
  }
`;

export const CodeEditor = themed(styled.div`
  padding: 0;
  position: relative;
  counter-reset: line;
  max-height: calc(100% - 5px);

  ${LinesSeparator}
`);

export const Pre = styled.pre`
  width: fit-content;
  margin: 0;
  min-width: 100%;

  > code > p {
    margin: 3px;
    line-height: 14px;
    color: ${themeStyle({
      dark: 'rgba(255, 255, 255, 0.9)',
      light: Colors.uiLight06
    })};
    font-family: IBMPlexMono;
    font-size: 14px;

    &:before {
      left: 5px;
      position: sticky;
      overflow-x: hidden;
      font-size: 12px;
      box-sizing: border-box;
      text-align: right;
      vertical-align: top;
      line-height: 17px;
      counter-increment: line;
      content: counter(line);
      color: ${themeStyle({
        dark: '#ffffff',
        light: Colors.uiLight06
      })};
      display: inline-block;
      width: 35px;
      opacity: ${themeStyle({
        dark: 0.5,
        light: 0.65
      })};
      padding-right: 13px;
      -webkit-user-select: none;
    }
  }
`;
