/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';

export const Table = themed(styled.table`
  width: 100%;
  font-size: 14px;
  border-spacing: 0;
  border-collapse: collapse;
`);

export const TH = themed(styled.th`
  font-weight: 600;
  padding: 0 0 0 5px;
  color: ${themeStyle({
    dark: 'rgba(255, 255, 255, 0.8)',
    light: 'rgba(98, 98, 110, 0.8)',
  })};

  &:not(:last-child):after {
    content: ' ';
    float: right;
    height: 31px;
    margin-top: 3px;
    width: 1px;
    background: ${themeStyle({
      dark: Colors.uiDark04,
      light: Colors.uiLight05,
    })};
  }
`);

export const TD = themed(styled.td`
  padding: 0 0 0 5px;
  white-space: nowrap;
  color: ${themeStyle({
    dark: 'rgba(255, 255, 255, 0.9)',
    light: 'rgba(98, 98, 110, 0.9)',
  })};
`);

export const TR = themed(styled.tr`
  height: 36px;
  line-height: 37px;

  border-width: 1px 0;
  border-style: solid;
  border-color: ${themeStyle({
    dark: Colors.uiDark04,
    light: Colors.uiLight05,
  })};

  &:nth-child(odd) {
    background-color: ${({theme, selected}) => {
      if (selected) {
        if (theme === 'dark') {
          return Colors.darkSelectedOdd;
        } else {
          return Colors.lightSelectedOdd;
        }
      } else {
        if (theme === 'dark') {
          return Colors.uiDark02;
        } else {
          return Colors.uiLight04;
        }
      }
    }};
  }

  &:nth-child(even) {
    background-color: ${({theme, selected}) => {
      if (selected) {
        if (theme === 'dark') {
          return Colors.darkSelectedEven;
        } else {
          return Colors.lightSelectedEven;
        }
      } else {
        if (theme === 'dark') {
          return '#37383e';
        } else {
          return '#f9fafc';
        }
      }
    }};
  }
`);

export const THead = themed(styled.thead`
  text-align: left;
  background-color: ${themeStyle({
    dark: Colors.uiDark04,
    light: Colors.uiLight02,
  })};

  ${TR.WrappedComponent} {
    background-color: ${themeStyle({
      dark: Colors.uiDark03,
      light: Colors.uiLight02,
    })};
  }
`);
