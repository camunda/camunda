/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';

import {themed, getIconButtonTheme as getTheme} from 'modules/theme';
import {SIZES, DEFAULT_SIZE} from './constants';

function setSize(props) {
  const size = SIZES[props.size] ? SIZES[props.size] : SIZES[DEFAULT_SIZE];
  return `height: ${size}px; width: ${size}px;`;
}

export const Icon = themed(styled.div`
  border-radius: 50%;
  border-color: none;
  ${setSize};

  position: relative;
  z-index: 1;

  svg {
    // default icon color/opacity
    ${props => getTheme(props.iconButtonTheme).default.icon[props.theme]}};
  }

  &:before {
    border-radius: 50%;
    position: absolute;
    left: 0;
    content: '';
    ${setSize}

    z-index: -1;

    // default background color/opacity
    ${props => getTheme(props.iconButtonTheme).default.background[props.theme]};
  }
`);

export const Button = themed(styled.button`
  display: flex;
  padding: 0;
  background: transparent;

  border-radius: 50%;

  &:hover {
    ${Icon.WrappedComponent}::before {
      // hover background color/opacity
      ${props =>
        !props.disabled &&
        getTheme(props.iconButtonTheme).hover.background[props.theme]}

      transition: background 0.15s ease-out;
    }

    ${Icon.WrappedComponent} {
      svg {
        // hover icon color/opacity
        ${props => getTheme(props.iconButtonTheme).hover.icon[props.theme]};
      }
    }
  }

  &:active {
    ${Icon.WrappedComponent}::before {
      // active background color/opacity
      ${props => getTheme(props.iconButtonTheme).active.background[props.theme]}
    }

    ${Icon.WrappedComponent} {
      svg {
        // active icon color/opacity
        ${props => getTheme(props.iconButtonTheme).active.icon[props.theme]};
      }
    }
  }
`);
