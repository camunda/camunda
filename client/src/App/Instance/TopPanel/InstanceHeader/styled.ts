/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
import StateIconDefault from 'modules/components/StateIcon';

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

const Container = styled.header`
  ${({theme}) => {
    const colors = theme.colors.topPanel.instanceHeader;

    return css`
      background-color: ${colors.backgroundColor};
      color: ${theme.colors.text01};
      border-bottom: solid 1px ${theme.colors.borderColor};
      display: flex;
      align-items: center;
      padding: 9px 10px 9px 20px;
    `;
  }}
`;

export {Table, Td, Th, StateIcon, StateIconWrapper, Container};
