/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';

export const Option = themed(styled.div`
  /* Display & Box Model */
  display: flex;
  align-items: center;
  height: 36px;
  width: 100%;

  /* Text */
  text-align: left;
  font-size: 15px;
  font-weight: 600;
  line-height: 36px;

  /* Add Border between options */
  &:not(:last-child) {
    border-bottom: 1px solid
      ${themeStyle({
        dark: Colors.uiDark06,
        light: Colors.uiLight05,
      })};
  }
`);

export const OptionButton = themed(styled.button`
  position: relative;

  /* Display & Box Model */
  display: flex;
  align-items: center;
  width: 100%;
  height: 100%;
  padding: 0 10px;

  border: none;
  background: none;

  /* Color */
  color: ${({disabled}) =>
    disabled
      ? themeStyle({
          dark: 'rgba(255, 255, 255, 0.6)',
          light: 'rgba(98, 98, 110, 0.6);',
        })
      : themeStyle({
          dark: 'rgba(255, 255, 255, 0.9)',
          light: 'rgba(98, 98, 110, 0.9)',
        })};

  /* Text */
  text-align: left;
  font-size: 15px;
  font-weight: 600;
  line-height: 36px;

  /* Other */
  ${({disabled}) => (!disabled ? interactionStyles : '')}
`);

const interactionStyles = css`
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
`;
