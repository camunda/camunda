/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
import {default as SplitPaneComponent} from 'modules/components/SplitPane';
import StateIconDefault from 'modules/components/StateIcon';

import {Colors, themed, themeStyle} from 'modules/theme';

const pseudoBorder = css`
  /* Border with individual z-index to be layered above child */
  &:before {
    position: absolute;
    content: '';
    height: 1px;
    width: 100%;
    z-index: 5;
    top: 0px;
    left: 0px;
    border-top: solid 1px
      ${themeStyle({
        dark: Colors.uiDark04,
        light: Colors.uiLight05
      })};
  }
`;

export const Pane = themed(styled(SplitPaneComponent.Pane)`
  border-top: none;
${({expandState}) => expandState === 'DEFAULT' && 'height: 50%'}
  background-color: ${themeStyle({
    dark: Colors.uiDark02,
    light: Colors.uiLight04
  })};
  ${pseudoBorder}
`);

export const SplitPaneHeader = styled(SplitPaneComponent.Pane.Header)`
  display: flex;
  align-items: center;
  z-index: 4;
  border-bottom: none;
`;

export const Table = styled.table`
  width: 100%;
  border-spacing: 0;
  position: relative;
  left: -2px;
`;

export const Tr = styled.tr`
  display: flex;
  justify-content: space-between;
  align-items: center;
`;

export const Td = styled.td`
  display: inline-block;
`;

export const ActionsWrapper = styled.div`
  width: 250px;
`;

export const SplitPaneBody = themed(styled(SplitPaneComponent.Pane.Body)`
  position: relative;
  border: none;
  ${pseudoBorder}
`);

export const StateIcon = styled(StateIconDefault)`
  margin-right: 8px;
`;
