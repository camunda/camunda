/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import StateIconDefault from 'modules/components/StateIcon';
import {styles} from '@carbon/elements';

const Table = styled.table`
  width: 100%;
  border-spacing: 0;
  position: relative;
  left: -2px;
  table-layout: fixed;
`;

const Th = styled.th`
  text-align: left;
  ${styles.label01};
`;

const Td = styled.td`
  ${styles.bodyShort01};
  font-weight: 500;
  text-overflow: ellipsis;
  overflow: hidden;
  white-space: nowrap;
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
      height: 56px;
    `;
  }}
`;

export {Table, Td, Th, StateIcon, StateIconWrapper, Container};
