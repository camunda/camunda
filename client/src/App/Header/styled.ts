/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';

import {ReactComponent as Logo} from 'modules/components/Icon/logo-2020-round.svg';

const HEADER_HEIGHT = 56;

const Header = styled.header`
  ${({theme}) => {
    const colors = theme.colors.header;

    return css`
      display: flex;
      height: ${HEADER_HEIGHT}px;
      background-color: ${theme.colors.ui01};
      padding: 15px 20px;
      font-size: 15px;
      font-weight: 500;
      color: ${colors.color};
    `;
  }}
`;

const Menu = styled.ul`
  display: flex;
  flex-wrap: wrap;
`;

const LogoIcon = styled(Logo)`
  ${({theme}) => {
    return css`
      color: ${theme.colors.logo};
      width: 26px;
      height: 26px;
      cursor: pointer;
    `;
  }}
`;

const Separator = styled.div`
  ${({theme}) => {
    const colors = theme.colors.header;

    return css`
      width: 1px;
      height: 24px;
      background-color: ${colors.separator};
      margin: 0 20px;
    `;
  }}
`;

export {HEADER_HEIGHT, Header, Menu, LogoIcon, Separator};
