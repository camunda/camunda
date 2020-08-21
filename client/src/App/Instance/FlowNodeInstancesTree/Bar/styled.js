/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';

import BasicFlowNodeIcon from 'modules/components/FlowNodeIcon';

export const NodeIcon = themed(styled(BasicFlowNodeIcon)`
  color: ${themeStyle({
    dark: '#fff',
    light: Colors.uiLight06,
  })};

  opacity: ${({isSelected}) =>
    isSelected
      ? themeStyle({
          dark: 0.8,
          light: 0.65,
        })
      : themeStyle({
          dark: 0.75,
          light: 0.6,
        })};
`);

const selectionStyle = css`
  border-color: ${themeStyle({
    dark: Colors.uiDark04,
    light: Colors.uiLight05,
  })};
  border-width: 1px 0px 0px 1px;
  background: ${themeStyle({
    dark: Colors.darkSelectedOdd,
    light: Colors.lightSelectedOdd,
  })};
  color: #fff;

  /* Bottom Border */
  &:before {
    content: '';
    position: absolute;
    bottom: 0px;
    left: 0px;
    width: 100%;
    height: 1px;
    z-index: 1;
  }
`;

export const Bar = themed(styled.div`
  display: flex;
  height: 27px;
  font-size: 13px;
  min-width: 200px;
  align-items: center;
  flex-grow: 1;
  background: ${themeStyle({
    dark: Colors.darkItemEven,
    light: Colors.lightItemEven,
  })};

  border-color: ${themeStyle({
    dark: Colors.uiDark04,
    light: Colors.uiLight05,
  })};
  border-width: 1px 0px 0px 1px;
  border-style: solid;

  ${({showSelectionStyle}) => showSelectionStyle && selectionStyle};
`);

export const NodeName = themed(styled.span`
  margin-left: 5px;
  padding-left: 5px;
  border-left: 1px solid
    ${({isSelected}) =>
      isSelected
        ? themeStyle({
            dark: 'rgba(255,255,255,0.25)',
            light: 'rgba(98, 98, 110,0.25)',
          })
        : themeStyle({
            dark: Colors.uiDark04,
            light: Colors.uiLight05,
          })};

  color: ${themeStyle({
    dark: '#fff',
    light: Colors.uiLight06,
  })};

  opacity: ${({isSelected}) =>
    isSelected
      ? themeStyle({
          dark: 0.9,
          light: 1,
        })
      : themeStyle({
          dark: 0.9,
          light: 0.9,
        })};

  font-weight: ${({isBold}) => (isBold ? 'bold' : '')};
`);
