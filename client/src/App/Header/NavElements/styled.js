/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';
import {Link} from 'react-router-dom';
import {Colors, themed, themeStyle} from 'modules/theme';
import withStrippedProps from 'modules/utils/withStrippedProps';
import {ReactComponent as Logo} from 'modules/components/Icon/logo-2020-round.svg';

const separator = themeStyle({
  dark: 'rgba(246, 252, 251, 0.5)',
  light: 'rgba(98, 98, 110, 0.25)',
});
export const ListItem = styled.li`
  display: flex;
  align-items: center;
`;
export const NavigationLabel = styled.span`
  margin-right: 4px;
  ${({isActive}) => (isActive ? '' : `opacity: 0.5;`)};
`;
export const DashboardLabel = styled.span`
  ${({isActive}) => (isActive ? '' : `opacity: 0.5;`)};
`;
export const BrandLabel = themed(styled.span`
  color: ${themeStyle({
    dark: Colors.logoDark,
    light: Colors.logoLight,
  })};
`);

export const LogoIcon = themed(styled(Logo)`
  position: relative;
  color: ${themeStyle({
    dark: Colors.logoDark,
    light: Colors.logoLight,
  })};

  width: 26px;
  height: 26px;
  margin-right: 19px;
`);
export const Brand = themed(styled(
  withStrippedProps(['toggleTheme', 'isActive'])(Link)
)`
  display: flex;
  align-items: center;
  padding-right: 26px;
`);
export const DashboardLink = themed(styled(
  withStrippedProps(['toggleTheme', 'isActive'])(Link)
)`
  display: inline-block;
  padding: 2px 21px 2px 20px
  border-right: 1px solid ${separator};
  border-left: 1px solid ${separator};
`);

export const ListLink = themed(styled(
  withStrippedProps(['toggleTheme', 'isActive'])(Link)
)`
  margin-left: 21px;
  display: flex;
  height: 20px;
  align-items: center;
`);
