/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
import BaseInstancesBar, {Wrapper, Bar} from 'modules/components/InstancesBar';
import {Colors, themed, themeStyle} from 'modules/theme';

export const Li = styled.li`
  margin: 0 10px 10px 0;
`;

export const VersionUl = styled.ul`
  margin-top: 8px;
  margin-bottom: 16px;
`;

export const VersionLi = styled.li`
  margin: 6px 0 0;
  padding: 0;
`;

const InstancesBarStyles = css`
  ${Bar.WrappedComponent} {
    border-radius: 2px;
    opacity: 1;
    background: ${themeStyle({
      dark: Colors.uiDark05,
      light: Colors.uiLight05
    })};
  }
`;

export const LiInstancesBar = themed(styled(BaseInstancesBar)`
  ${InstancesBarStyles};

  ${Wrapper.WrappedComponent} {
    color: ${Colors.incidentsAndErrors};
  }
`);

export const VersionLiInstancesBar = themed(styled(BaseInstancesBar)`
  ${InstancesBarStyles}
`);
