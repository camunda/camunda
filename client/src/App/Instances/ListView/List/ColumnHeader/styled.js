/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';
import DefaultSortIcon from 'modules/components/SortIcon';

const opacityLabel = {
  dark: {
    default: '0.7',
    active: '0.9',
    disabled: '0.5'
  },
  light: {
    default: '0.8',
    active: '1',
    disabled: '0.6'
  }
};
const opacityIcon = {
  dark: {
    default: '0.6',
    active: '0.9',
    disabled: '0.3'
  },
  light: {
    default: '0.6',
    active: '1',
    disabled: '0.3'
  }
};

export const ColumnHeader = themed(
  styled.span`
    color: ${themeStyle({
      dark: '#fff',
      light: Colors.uiLight06
    })};

    cursor: default;
  `
);

export const SortColumnHeader = themed(
  styled.button`
    color: ${themeStyle({
      dark: '#fff',
      light: Colors.uiLight06
    })};
    cursor: ${({disabled}) => (disabled ? 'default' : 'pointer')};

    padding: 0;
    margin: 0;
    background: transparent;
    border: 0;
    display: inline-block;
    font-weight: bold;
    font-size: 14px;
    line-height: 37px;
  `
);

export const Label = themed(
  styled.span`
    opacity: ${themeStyle({
      dark: ({active, disabled}) => {
        return active || disabled
          ? opacityLabel.dark[disabled ? 'disabled' : 'active']
          : opacityLabel.dark.default;
      },
      light: ({active, disabled}) => {
        return active || disabled
          ? opacityLabel.light[disabled ? 'disabled' : 'active']
          : opacityLabel.light.default;
      }
    })};
  `
);

export const SortIcon = themed(styled(DefaultSortIcon)`
  position: relative;
  top: 2px;
  margin-left: 4px;

  opacity: ${themeStyle({
    dark: ({active, disabled}) => {
      return active || disabled
        ? opacityIcon.dark[disabled ? 'disabled' : 'active']
        : opacityIcon.dark.default;
    },
    light: ({active, disabled}) => {
      return active || disabled
        ? opacityIcon.light[disabled ? 'disabled' : 'active']
        : opacityIcon.light.default;
    }
  })};
`);
