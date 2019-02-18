/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';

import {themed, themeStyle, Colors} from 'modules/theme';
import {ReactComponent as Down} from 'modules/components/Icon/down.svg';
import {ReactComponent as Right} from 'modules/components/Icon/right.svg';
import withStrippedProps from 'modules/utils/withStrippedProps';

const iconStyle = css`
  width: 16px;
  height: 16px;
  object-fit: contain;
  color: ${({theme}) => {
    return theme === 'dark' ? 'rgba(255, 255, 255, 0.9)' : Colors.uiDark04;
  }};
`;

export const FoldButton = themed(styled.button`
  background: transparent;
  border-radius: 50%;
  border-color: none;
  padding: 0;
  height: 16px;
  width: 16px;
  position: absolute;
  left: -24px;
  top: 6px;
  z-index: 2;

  &:hover {
    background: ${themeStyle({
      dark: Colors.darkTreeHover,
      light: 'rgba(216, 220, 227, 0.5)'
    })};
  }

  &:active {
    background: ${themeStyle({
      dark: 'rgba(255, 255, 255, 0.4)',
      light: 'rgba(216, 220, 227, 0.8)'
    })};
  }
`);

export const DownIcon = themed(styled(withStrippedProps(['isSelected'])(Down))`
  ${iconStyle};
`);

export const RightIcon = themed(styled(
  withStrippedProps(['isSelected'])(Right)
)`
  ${iconStyle};
`);

export const Summary = themed(styled.div`
  position: relative;
  height: 27px;
`);

const partialBorder = css`
  &:before {
    content: '';
    position: absolute;
    height: 1px;
    width: 32px;
    bottom: -1px;
    z-index: 1;
    background: ${({isSelected}) =>
      isSelected
        ? 'none'
        : themeStyle({
            dark: Colors.uiDark04,
            light: Colors.uiLight05
          })};
  }
`;

const fullBorder = css`
  border-bottom-color: ${themeStyle({
    dark: Colors.uiDark04,
    light: Colors.uiLight05
  })};
  border-bottom-width: 1px;
  border-bottom-style: solid;
`;

export const SummaryLabel = themed(styled.div`
  position: absolute;
  left: 0;
  top: 0;
  width: 100%;
  margin: 0;
  padding: 0;
  border: none;
  font-size: 14px;
  text-align: left;
  ${({showFullBorder, isSelected}) =>
    showFullBorder && !isSelected && fullBorder};
  ${({showPartialBorder}) => showPartialBorder && partialBorder};
`);

export const FocusButton = themed(styled.button`
  position: absolute;
  background: transparent;
  left: 1px;
  top: 1px;
  height: calc(100% - 2px);
  width: calc(100% - 5px);
  z-index: 2;

  /* Apply hover style to <Bar/>*/
  &:hover + div > div {
    background: ${({showHoverState}) =>
      showHoverState &&
      themeStyle({
        dark: Colors.uiDark04,
        light: Colors.lightButton05
      })};
  }
`);

export const Details = themed(styled.div`
  display: ${({showChildScope}) => (showChildScope ? 'block' : 'none')};
`);
