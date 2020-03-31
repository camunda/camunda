/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';
import {INCIDENTS_BAR_HEIGHT} from 'modules/constants';

import BgDark from './bg-dark@2x.png';
import BgLight from './bg-light@2x.png';

export const Overlay = themed(styled.div`
  width: 100%;
  height: 100%;
  padding-top: ${INCIDENTS_BAR_HEIGHT}px;
  padding-left: 52px;
  position: absolute;
  z-index: 3;
  top: 0;
  bottom: 0;
  left: 0;
  right: 0;
  overflow: auto;

  background-image: ${themeStyle({
    dark: `url(${BgDark})`,
    light: `url(${BgLight})`,
  })};
  background-repeat: repeat-y;
  background-position: fixed;
  background-color: ${themeStyle({
    dark: Colors.uiDark02,
    light: Colors.uiLight04,
  })};
  background-size: 51px;
`);
