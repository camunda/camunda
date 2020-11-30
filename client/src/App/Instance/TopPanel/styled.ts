/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
import {default as SplitPaneComponent} from 'modules/components/SplitPane';
import StateIconDefault from 'modules/components/StateIcon';
import {StatusMessage} from 'modules/components/StatusMessage';

const pseudoBorder = ({theme}: any) => {
  const colors = theme.colors.topPanel.pseudoBorder;

  return css`
    /* Border with individual z-index to be layered above child */
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
};

const Pane = styled(SplitPaneComponent.Pane)`
  ${({theme, expandState}) => {
    const colors = theme.colors.topPanel.pane;

    return css`
      border-top: none;
      background-color: ${colors.backgroundColor};
      ${pseudoBorder}
      ${expandState === 'DEFAULT'
        ? css`
            height: 50%;
          `
        : ''}
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
`;

const Tr = styled.tr`
  display: flex;
  justify-content: space-between;
  align-items: center;
`;

const Td = styled.td`
  display: inline-block;
`;

const OperationsWrapper = styled.div`
  width: 250px;
`;

const SplitPaneBody = styled(SplitPaneComponent.Pane.Body)`
  position: relative;
  border: none;
  ${pseudoBorder}

  ${StatusMessage} {
    height: 100%;
  }
`;

const StateIcon = styled(StateIconDefault)`
  margin-right: 8px;
`;

export {
  Pane,
  SplitPaneHeader,
  Table,
  Tr,
  Td,
  OperationsWrapper,
  SplitPaneBody,
  StateIcon,
};
