/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';
import BaseInstancesBar from 'modules/components/InstancesBar';
import {Link} from 'react-router-dom';
import {Colors, themed, themeStyle} from 'modules/theme';

export const Panel = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 16px 104px 41px 104px;
`;

export const InstancesBar = styled(BaseInstancesBar)`
  align-self: stretch;
`;

export const SkeletonBar = themed(styled.div`
  width: 100%;
  height: 15px;
  margin-top: 51px;

  background: ${themeStyle({
    dark: 'rgba(136, 136, 141)',
    light: Colors.uiLight06
  })};
  opacity: ${themeStyle({
    dark: 0.2,
    light: 0.09
  })};
`);

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
