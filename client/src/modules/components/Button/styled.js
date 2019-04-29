/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';

import {Colors, themed, themeStyle} from 'modules/theme';

const sizeStyle = ({size}) => {
  const smallSizeStyle = css`
    height: 22px;

    font-size: 12px;
  `;

  const mediumSizeStyle = css`
    height: 35px;
    width: 117px;

    font-size: 14px;
  `;

  const largeSizeStyle = css`
    height: 48px;
    width: 340px;

    font-size: 18px;
  `;

  const style = {
    small: smallSizeStyle,
    medium: mediumSizeStyle,
    large: largeSizeStyle
  };

  return style[size];
};

const getBoxShadow = ({size}) => {
  const shadow = css`
    box-shadow: 0 2px 2px 0
      ${themeStyle({
        dark: 'rgba(0, 0, 0, 0.35)',
        light: 'rgba(0, 0, 0, 0.08)'
      })};
  `;

  return size === 'small' ? '' : shadow;
};

const colorStyle = ({color}) => {
  const primaryStyle = css`
    box-shadow: 0 2px 2px 0 rgba(0, 0, 0, 0.35);
    background-color: ${Colors.selections};
    border: 1px solid ${Colors.primaryButton01};
    color: ${Colors.uiLight02};

    &:hover {
      background-color: ${Colors.primaryButton03};
    }

    &:focus {
      border-color: ${Colors.primaryButton01};
    }

    &:active {
      background-color: ${Colors.primaryButton01};
      border-color: ${Colors.primaryButton02};
    }

    &:disabled {
      color: rgba(247, 248, 250, 0.6);
      border-color: ${Colors.primaryButton03};
    }
  `;

  const mainStyle = css`
    color: ${themeStyle({
      dark: Colors.uiLight02,
      light: 'rgba(69, 70, 78, 0.9)'
    })};

    background-color: ${themeStyle({
      dark: Colors.uiDark05,
      light: Colors.uiLight05
    })};

    border: 1px solid
      ${themeStyle({
        dark: Colors.uiDark06,
        light: Colors.uiLight03
      })};

    &:hover {
      background-color: ${themeStyle({
        dark: '#6b6f74',
        light: '#cdd4df'
      })};
      border-color: ${themeStyle({
        dark: Colors.darkButton02,
        light: '#9ea9b7'
      })};
    }

    &:focus {
      border-color: ${themeStyle({
        dark: Colors.uiDark06,
        light: Colors.uiLight03
      })};
    }

    &:active {
      background-color: ${themeStyle({
        dark: Colors.uiDark04,
        light: Colors.uiLight03
      })};
      border-color: ${themeStyle({
        dark: Colors.uiDark05,
        light: '#88889a'
      })};
    }

    &:disabled {
      cursor: not-allowed;

      background-color: ${themeStyle({
        dark: '#34353a',
        light: '#f1f2f5'
      })};
      border-color: ${themeStyle({
        dark: Colors.uiDark05,
        light: Colors.uiLight03
      })};
      color: ${themeStyle({
        dark: 'rgba(247, 248, 250, 0.5)',
        light: 'rgba(69, 70, 78, 0.5)'
      })};
      box-shadow: none;
    }
  `;

  return color === 'primary' ? primaryStyle : mainStyle;
};

export const Button = themed(styled.button`
  border-radius: ${({size}) => (size === 'small' ? '11px' : '3px')};
  ${props => getBoxShadow(props)};
  font-family: IBMPlexSans;
  font-weight: 600;

  ${colorStyle};
  ${sizeStyle};
`);
