/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
import {Link} from 'react-router-dom';
import {ReactComponent as Logo} from 'modules/components/Icon/logo-2020-round.svg';

const ListItem = styled.li`
  display: flex;
  align-items: center;
`;

const NavigationLabel = styled.span`
  ${({$isActive}) => {
    return css`
      margin-right: 4px;
      ${$isActive
        ? ''
        : css`
            opacity: 0.5;
          `}
    `;
  }}
`;

const DashboardLabel = styled.span`
  ${({$isActive}) => {
    return css`
      ${$isActive
        ? ''
        : css`
            opacity: 0.5;
          `}
    `;
  }}
`;

const BrandLabel = styled.span`
  ${({theme}) => {
    return css`
      color: ${theme.colors.logo};
    `;
  }}
`;

const LogoIcon = styled(Logo)`
  ${({theme}) => {
    return css`
      position: relative;
      color: ${theme.colors.logo};
      width: 26px;
      height: 26px;
      margin-right: 19px;
    `;
  }}
`;
const Brand = styled(Link)`
  display: flex;
  align-items: center;
  padding-right: 26px;
`;

const DashboardLink = styled(Link)`
  ${({theme}) => {
    const colors = theme.colors.header.navElements;

    return css`
      display: inline-block;
      padding: 2px 21px 2px 20px;
      border-right: 1px solid ${colors.borderColor};
      border-left: 1px solid ${colors.borderColor};
    `;
  }}
`;

const ListLink = styled(Link)`
  margin-left: 21px;
  display: flex;
  height: 20px;
  align-items: center;
`;

export {
  ListItem,
  NavigationLabel,
  DashboardLabel,
  BrandLabel,
  LogoIcon,
  Brand,
  DashboardLink,
  ListLink,
};
