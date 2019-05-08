/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';

import withStrippedProps from 'modules/utils/withStrippedProps';
import {ReactComponent as Down} from 'modules/components/Icon/down.svg';
import {ReactComponent as Right} from 'modules/components/Icon/right.svg';

import {themed, themeStyle, ExpandColors} from 'modules/theme';

const iconStyle = css`
  width: 16px;
  height: 16px;
  object-fit: contain;
`;

export const DownIcon = styled(withStrippedProps(['isSelected'])(Down))`
  ${iconStyle};
`;

export const RightIcon = styled(withStrippedProps(['isSelected'])(Right))`
  ${iconStyle};
`;

export const Icon = themed(styled.div`
  border-radius: 50%;
  border-color: none;
  height: 16px;
  width: 16px;
  z-index: 1;

  // default arrow color/opacity
  color: ${themeStyle({
    dark: props => ExpandColors[props.expandTheme].default.arrow.dark.color,
    light: props => ExpandColors[props.expandTheme].default.arrow.light.color
  })};
  svg {
    opacity: ${themeStyle({
      dark: props => ExpandColors[props.expandTheme].default.arrow.dark.opacity,
      light: props =>
        ExpandColors[props.expandTheme].default.arrow.light.opacity
    })};
  }

  &:before {
    border-radius: 50%;
    position: absolute;
    content: '';
    height: 16px;
    width: 16px;
    z-index: -1;

    // default background color/opacity
    background: ${themeStyle({
      dark: props =>
        ExpandColors[props.expandTheme].default.background.dark.color,
      light: props =>
        ExpandColors[props.expandTheme].default.background.light.color
    })};
    opacity: ${themeStyle({
      dark: props =>
        ExpandColors[props.expandTheme].default.background.dark.opacity,
      light: props =>
        ExpandColors[props.expandTheme].default.background.light.opacity
    })};
  }
`);

export const Button = themed(styled.button`
  display: flex;
  padding: 0;
  background: transparent;

  :hover {
    // hover background color/opacity
    ${Icon.WrappedComponent}::before {
      background: ${themeStyle({
        dark: props =>
          ExpandColors[props.expandTheme].hover.background.dark.color,
        light: props =>
          ExpandColors[props.expandTheme].hover.background.light.color
      })};
      opacity: ${themeStyle({
        dark: props =>
          ExpandColors[props.expandTheme].hover.background.dark.opacity,
        light: props =>
          ExpandColors[props.expandTheme].hover.background.light.opacity
      })};
    }

    // hover arrow color/opacity
    ${Icon.WrappedComponent} {
      color: ${themeStyle({
        dark: props => ExpandColors[props.expandTheme].hover.arrow.dark.color,
        light: props => ExpandColors[props.expandTheme].hover.arrow.light.color
      })};

      opacity: ${themeStyle({
        dark: props => ExpandColors[props.expandTheme].hover.arrow.dark.opacity,
        light: props =>
          ExpandColors[props.expandTheme].hover.arrow.light.opacity
      })};
  }

  :active {
    // active background color/opacity
    ${Icon.WrappedComponent}::before {
      background: ${themeStyle({
        dark: props =>
          ExpandColors[props.expandTheme].active.background.dark.color,
        light: props =>
          ExpandColors[props.expandTheme].active.background.light.color
      })};
      opacity: ${themeStyle({
        dark: props =>
          ExpandColors[props.expandTheme].active.background.dark.opacity,
        light: props =>
          ExpandColors[props.expandTheme].active.background.light.opacity
      })};
    }

    // active arrow color/opacity
    ${Icon.WrappedComponent} {
      color: ${themeStyle({
        dark: props => ExpandColors[props.expandTheme].active.arrow.dark.color,
        light: props => ExpandColors[props.expandTheme].active.arrow.light.color
      })};
      opacity: ${themeStyle({
        dark: props =>
          ExpandColors[props.expandTheme].active.arrow.dark.opacity,
        light: props =>
          ExpandColors[props.expandTheme].active.arrow.light.opacity
      })};
  }
`);
