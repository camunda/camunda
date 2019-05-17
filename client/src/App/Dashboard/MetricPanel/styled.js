/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';
import BaseInstancesBar from 'modules/components/InstancesBar';
import {Link} from 'react-router-dom';

export const Panel = themed(styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 16px 104px 41px 104px;

  color: ${themeStyle({
    dark: 'rgba(255, 255, 255, 0.9)',
    light: Colors.uiLight06
  })};

  border-radius: 3px;
  border: solid 1px
    ${themeStyle({dark: Colors.uiDark04, light: Colors.uiLight05})};
  background-color: ${themeStyle({
    dark: Colors.uiDark02,
    light: Colors.uiLight04
  })};
  box-shadow: ${themeStyle({
    dark: '0 3px 6px 0 #000000',
    light: '0 2px 3px 0 rgba(0, 0, 0, 0.1)'
  })};
`);

export const InstancesBar = styled(BaseInstancesBar)`
  align-self: stretch;
`;

export const Title = styled(Link)`
  font-size: 30px;
  line-height: 60px;
  margin-bottom: -27px;
  &:hover {
    text-decoration: underline;
  }
  z-index: 1;
`;

export const LabelContainer = styled.div`
  margin-top: 9px;
  width: 100%;
  display: flex;
  justify-content: space-between;
`;

export const Label = styled(Link)`
  font-size: 24px;
  &:hover {
    text-decoration: underline;
  }
`;
