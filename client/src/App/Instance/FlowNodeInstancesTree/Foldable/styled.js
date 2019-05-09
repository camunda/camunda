/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';

import {themed, themeStyle, Colors} from 'modules/theme';
import BasicExpandButton from 'modules/components/ExpandButton';

export const ExpandButton = styled(BasicExpandButton)`
  position: absolute;
  left: -24px;
  top: 6px;
  z-index: 2;
`;

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
        dark: Colors.darkTreeHover,
        light: Colors.lightButton05
      })};
  }
`);

export const Details = themed(styled.div`
  display: ${({showChildScope}) => (showChildScope ? 'block' : 'none')};
`);
