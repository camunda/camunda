/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css, ThemedInterpolationFunction} from 'styled-components';

import {NavElement} from './NavElements';
import * as Styled from './NavElements/styled';
import BasicStateIcon from 'modules/components/StateIcon';

const HEADER_HEIGHT = 56;

const StateIcon = styled(BasicStateIcon)`
  top: 0px;
  min-width: 16px;
  min-height: 16px;
`;

const FilterNavElement = styled(NavElement)`
  ${Styled.NavigationLabel} {
    margin-right: 3px;
  }
`;

const NavListItem = styled.div`
  display: flex;
  align-items: center;
`;

const Header = styled.header`
  ${({theme}) => {
    const colors = theme.colors.header;

    return css`
      display: flex;
      height: ${HEADER_HEIGHT}px;
      background-color: ${theme.colors.ui01};
      padding: 9px 20px 21px 20px;
      font-size: 15px;
      font-weight: 500;
      color: ${colors.color};
      line-height: 19px;
    `;
  }}
`;

const Detail = styled.span`
  ${({theme}) => {
    const colors = theme.colors.header.details;

    return css`
      display: flex;
      align-items: center;
      align-self: center;
      padding: 12px 0 12px 20px;
      margin-left: 21px;
      height: 50%;
      border-left: 1px solid ${colors.borderColor};
    `;
  }}
`;

const Menu = styled.ul`
  display: flex;
  flex-wrap: wrap;
`;

const colors: ThemedInterpolationFunction = ({theme}) => {
  const colors = theme.colors.header.skeleton;

  return css`
    background: ${colors.backgroundColor};
  `;
};

const SkeletonBlock = styled.div`
  height: 14px;
  width: 120px;
  ${colors};
`;

const SkeletonCircle = styled.div`
  border-radius: 50%;
  margin-right: 11px;
  height: 14px;
  width: 14px;
  ${colors};
`;

export {
  HEADER_HEIGHT,
  StateIcon,
  FilterNavElement,
  NavListItem,
  Header,
  Detail,
  Menu,
  SkeletonBlock,
  SkeletonCircle,
};
