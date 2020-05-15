/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';

import {Colors, themed, themeStyle} from 'modules/theme';
import withStrippedProps from 'modules/utils/withStrippedProps';
import {NavElement} from './NavElements';
import * as Styled from './NavElements/styled';

export const HEADER_HEIGHT = 56;
const separator = themeStyle({
  dark: 'rgba(246, 252, 251, 0.5)',
  light: 'rgba(98, 98, 110, 0.25)',
});

export const FilterNavElement = styled(NavElement)`
  ${Styled.NavigationLabel} {
    margin-right: 3px;
  }
`;

export const NavListItem = styled.div`
  display: flex;
  align-items: center;
`;

export const Header = themed(styled(
  withStrippedProps(['toggleTheme'])('header')
)`
  display: flex;
  height: ${HEADER_HEIGHT}px;
  background-color: ${themeStyle({
    dark: Colors.uiDark01,
    light: Colors.uiLight01,
  })};
  padding: 9px 20px 21px 20px;
  font-size: 15px;
  font-weight: 500;
  color: ${themeStyle({
    dark: '#ffffff',
    light: Colors.uiLight06,
  })};
  line-height: 19px;
`);

export const Detail = themed(styled.span`
  display: flex;
  align-items: center;
  align-self: center;
  padding: 12px 0 12px 20px;
  margin-left: 21px;
  height: 50%;
  border-left: 1px solid ${separator};
`);

export const Menu = styled.ul`
  display: flex;
  flex-wrap: wrap;
`;

const colors = css`
  background: ${themeStyle({
    dark: 'rgba(136, 136, 141)',
    light: Colors.uiLight06,
  })};
  opacity: ${themeStyle({
    dark: 0.2,
    light: 0.09,
  })};
`;

export const SkeletonBlock = themed(styled.div`
  height: 14px;
  width: 120px;
  ${colors};
`);

export const SkeletonCircle = themed(styled.div`
  border-radius: 50%;
  margin-right: 11px;
  height: 14px;
  width: 14px;
  ${colors};
`);
