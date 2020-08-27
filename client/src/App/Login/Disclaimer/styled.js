/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';
import {themed, themeStyle, Colors} from 'modules/theme';

const Container = themed(styled.div`
  color: ${themeStyle({
    dark: 'rgba(255, 255, 255, 0.7)',
    light: '#7e7e7f',
  })};
  opacity: 0.9;
  font-size: 12px;
  margin-top: 35px;
  width: 489px;
`);

const Anchor = themed(styled.a`
  text-decoration: underline;
  &:link {
    color: ${themeStyle({
      dark: Colors.darkLinkDefault,
      light: Colors.lightLinkDefault,
    })};
  }

  &:hover {
    color: ${themeStyle({
      dark: Colors.darkLinkHover,
      light: Colors.lightLinkHover,
    })};
  }

  &:active {
    color: ${themeStyle({
      dark: Colors.darkLinkActive,
      light: Colors.lightLinkActive,
    })};
  }

  &:visited {
    color: ${themeStyle({
      dark: Colors.darkLinkVisited,
      light: Colors.lightLinkVisited,
    })};
  }
`);

export {Container, Anchor};
