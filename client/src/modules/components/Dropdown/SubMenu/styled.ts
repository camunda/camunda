/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';

const SubMenu = styled.div`
  position: relative;
  z-index: 2;
  width: 100%;
  height: 100%;
`;

type SubMenuButtonProps = {
  submenuActive?: boolean;
};

const SubMenuButton = styled.button<SubMenuButtonProps>`
  ${({theme, submenuActive}) => {
    const colors = theme.colors.modules.dropdown.subMenu.subMenuButton;

    return css`
      display: flex;
      justify-content: space-between;
      align-items: center;
      width: 100%;
      height: 100%;
      padding: 0 10px;
      border: none;
      border-radius: 0 0 2px 2px;
      background: ${submenuActive ? theme.colors.menuActive : 'none'};
      color: ${colors.color};
      font-size: 15px;
      font-weight: 600;
      text-align: left;
      line-height: 36px;

      &:hover {
        background: ${colors.hover.backgroundColor};
      }

      &:active {
        background: ${theme.colors.menuActive};
      }
    `;
  }}
`;

const Ul = styled.ul`
  ${({theme}) => {
    const colors = theme.colors.modules.dropdown.subMenu.ul;
    const shadow = theme.shadows.modules.dropdown.subMenu.ul;

    return css`
      position: absolute;
      right: -44px;
      bottom: 4px;
      width: 45px;
      padding-left: 0px;
      box-shadow: ${shadow};
      background: ${colors.backgroundColor};
      color: ${colors.color};
      border-radius: 3px;
      border: 1px solid ${colors.borderColor};
    `;
  }}
`;

const Li = styled.li`
  ${({theme}) => {
    const colors = theme.colors.modules.dropdown.subMenu.li;

    return css`
      &:not(:last-child) {
        border-bottom: 1px solid ${colors.borderColor};
      }

      &:first-child button {
        border-radius: 2px 2px 0 0;
      }
      &:last-child button {
        border-radius: 0 0 2px 2px;
      }

      &:first-child:last-child button {
        border-radius: 2px;
      }
    `;
  }}
`;

export {SubMenu, SubMenuButton, Ul, Li};
