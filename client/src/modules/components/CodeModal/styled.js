/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';

import Modal from 'modules/components/Modal';

export const ModalBody = themed(styled(Modal.Body)`
  padding: 0;
  position: relative;
  counter-reset: line;
  overflow: auto;

  & pre {
    margin: 0;
  }
`);

export const CodeLine = themed(styled.p`
  margin: 3px;
  margin-left: 0;
  line-height: 14px;
  color: ${themeStyle({
    dark: 'rgba(255, 255, 255, 0.9)',
    light: Colors.uiLight06
  })};
  font-family: IBMPlexMono;
  font-size: 14px;

  &:before {
    font-size: 12px;
    box-sizing: border-box;
    text-align: right;
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
    padding-right: 11px;
    -webkit-user-select: none;
  }
`);

export const LinesSeparator = themed(styled.span`
  position: absolute;
  top: 0;
  left: 33px;
  height: 100%;
  width: 1px;
  background-color: ${themeStyle({
    dark: Colors.uiDark02,
    light: Colors.uiLight05
  })};
`);
