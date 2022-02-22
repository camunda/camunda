/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
import BaseInstancesBar from 'modules/components/InstancesBar';
import {Link} from 'react-router-dom';

const Panel = styled.div`
  ${({theme}) => {
    const colors = theme.colors.metricPanel;
    return css`
      display: flex;
      flex-direction: column;
      align-items: center;
      color: ${colors.color};
    `;
  }}
`;

const InstancesBar = styled(BaseInstancesBar)`
  align-self: stretch;
`;

const SkeletonBar = styled.div`
  ${({theme}) => {
    const colors = theme.colors.metricPanel.skeletonBar;
    const opacity = theme.opacity.metricPanel.skeletonBar;
    return css`
      width: 100%;
      height: 15px;
      margin-top: 51px;
      background: ${colors.backgroundColor};
      opacity: ${opacity};
    `;
  }}
`;

const Title = styled(Link)`
  font-size: 30px;
  line-height: 60px;
  margin-bottom: -27px;
  &:hover {
    text-decoration: underline;
  }
`;

const LabelContainer = styled.div`
  margin-top: 9px;
  width: 100%;
  display: flex;
  justify-content: space-between;
`;

const Label = styled(Link)`
  font-size: 24px;
  &:hover {
    text-decoration: underline;
  }
`;

export {Panel, InstancesBar, SkeletonBar, Title, LabelContainer, Label};
