/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';

export const Pagination = styled.div`
  text-align: center;
`;

export const Page = themed(styled.button`
  color: ${themeStyle({dark: '#ffffff', light: Colors.uiDark02})};
  font-family: IBMPlexSans;
  font-size: 13px;
  padding: ${({withIcon}) => (withIcon ? '0' : '0 5px')};
  line-height: 18px;
  height: 18px;
  margin: 1px;
  vertical-align: top;
  ${({active, theme}) => {
    if (active) {
      return `background-color: rgba(77, 144, 255, 0.9);
    color: #ffffff;
    border: 1px solid #007dff;
    cursor: default;`;
    } else {
      if (theme === 'dark') {
        return `background-color: ${Colors.uiDark04};
      border: 1px solid ${Colors.uiDark05};`;
      } else {
        return `background-color: ${Colors.uiLight05};
      border: 1px solid ${Colors.uiLight03};`;
      }
    }
  }};

  ${({disabled, theme}) => {
    if (disabled) {
      if (theme === 'dark') {
        return 'background-color: #34353a; cursor: default; color: rgba(255,255,255,0.4)';
      } else {
        return `background-color: #f1f2f5; cursor: default; color: ${Colors.uiLight03}`;
      }
    }
  }};
`);

export const PageSeparator = styled.div`
  vertical-align: top;
  display: inline-block;
  opacity: 0.9;
  font-size: 13px;
  height: 18px;
  width: 18px;
  text-align: center;
  line-height: 18px;
`;
