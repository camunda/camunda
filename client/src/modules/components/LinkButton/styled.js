/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';

const LinkButton = themed(styled.button`
  padding: 0;
  margin: 0;
  background: transparent;
  border: 0;

  font-size:  ${({size}) => (size === 'small' ? '12px' : '14px')}};
  text-decoration: underline;

  color: ${themeStyle({
    dark: Colors.darkLinkDefault,
    light: Colors.lightLinkDefault,
  })};

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
`);

export {LinkButton};
