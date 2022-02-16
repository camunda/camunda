/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
import {default as SplitPaneComponent} from 'modules/components/SplitPane';
import StateIconDefault from 'modules/components/StateIcon';

const SplitPane = styled(SplitPaneComponent.Pane)`
  ${({theme}) => {
    const colors = theme.colors.instanceHeader;

    return css`
      border-top: none;
      background-color: ${colors.backgroundColor};
      &:before {
        position: absolute;
        content: '';
        height: 1px;
        width: 100%;
        z-index: 5;
        top: 0px;
        left: 0px;
        border-top: solid 1px ${colors.borderColor};
      }
    `;
  }}
`;

const SplitPaneHeader = styled(SplitPaneComponent.Pane.Header)`
  display: flex;
  align-items: center;
  z-index: 4;
  border-bottom: none;
`;

const Table = styled.table`
  width: 100%;
  border-spacing: 0;
  position: relative;
  left: -2px;
  table-layout: fixed;
`;

const Th = styled.th`
  text-align: left;
  font-size: 12px;
  font-weight: normal;
`;

const Td = styled.td`
  font-weight: 500;
  font-size: 15px;
`;

const StateIconWrapper = styled.div`
  padding-right: 8px;
`;

const StateIcon = styled(StateIconDefault)`
  width: 21px;
  height: 21px;
`;

export {SplitPane, SplitPaneHeader, Table, Td, Th, StateIcon, StateIconWrapper};
