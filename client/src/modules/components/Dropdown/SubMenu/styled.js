/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';

export const SubMenu = themed(styled.div`
  position: relative;
  z-index: 2;
  width: 100%;
  height: 100%;
`);

export const SubMenuButton = themed(styled.button`
  /* Display & Box Model */
  display: flex;
  justify-content: space-between;
  align-items: center;

  width: 100%;
  height: 100%;
  padding: 0 10px;

  border: none;
  border-radius: 0 0 2px 2px;

  /* Color */
  background: ${({submenuActive}) =>
    submenuActive
      ? themeStyle({
          dark: Colors.darkActive,
          light: Colors.lightActive,
        })
      : 'none'};
  color: ${themeStyle({
    dark: 'rgba(255, 255, 255, 0.9)',
    light: 'rgba(98, 98, 110, 0.9)',
  })};

  /* Text */
  font-size: 15px;
  font-weight: 600;
  text-align: left;
  line-height: 36px;

  &:hover {
    background: ${themeStyle({
      dark: Colors.uiDark06,
      light: Colors.uiLight05,
    })};
  }

  &:active {
    background: ${themeStyle({
      dark: Colors.darkActive,
      light: Colors.lightActive,
    })};
  }
`);

export const Ul = themed(styled.ul`
  /* Positioning */
  position: absolute;
  right: -44px;
  bottom: 4px;

  /* Display & Box Model */
  width: 45px;
  padding-left: 0px;
  box-shadow: 0 0 2px 0
    ${themeStyle({
      dark: 'rgba(0, 0, 0, 0.6)',
      light: ' rgba(0, 0, 0, 0.2)',
    })};

  /* Color */
  background: ${themeStyle({
    dark: Colors.uiDark04,
    light: Colors.uiLight02,
  })};
  color: ${themeStyle({
    dark: '#ffffff',
    light: Colors.uiLight06,
  })};

  /* Other */
  border-radius: 3px;
  border: 1px solid
    ${themeStyle({dark: Colors.uiDark06, light: Colors.uiLight05})};
`);

export const Li = themed(styled.li`
  /* Add Border between options */
  &:not(:last-child) {
    border-bottom: 1px solid
      ${themeStyle({
        dark: Colors.uiDark06,
        light: Colors.uiLight05,
      })};
  }

  &:first-child button {
    border-radius: 2px 2px 0 0;
  }
  &:last-child button {
    border-radius: 0 0 2px 2px;
  }

  /* Border radius if only one child exists */
  &:first-child:last-child button {
    border-radius: 2px 2px 2px 2px;
  }
`);
