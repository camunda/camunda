/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {CollapsablePanel as CollapsablePanelBase} from 'modules/components/CollapsablePanel';
import {styles} from '@carbon/elements';

type OperationsListProps = {
  isInitialLoadComplete?: boolean;
};

const OperationsList = styled.ul<OperationsListProps>`
  ${({theme, isInitialLoadComplete}) => {
    return css`
      li:first-child {
        border-top: none;
      }

      li:last-child {
        border-bottom: ${isInitialLoadComplete
          ? 'none'
          : theme.colors.borderColor};
      }
    `;
  }}
`;

const EmptyMessage = styled.div`
  ${({theme}) => {
    const colors = theme.colors.operationsPanel.emptyMessage;

    return css`
      border: 1px solid ${theme.colors.borderColor};
      border-radius: 3px;
      margin: 30px 17px 0 18px;
      padding: 29px 44px 29px 32px;
      text-align: center;
      ${styles.bodyShort01};
      color: ${colors.color};
      background-color: ${colors.backgroundColor};
    `;
  }}
`;

const CollapsablePanel = styled(CollapsablePanelBase)`
  ${({theme}) => {
    return css`
      border-left: 1px solid ${theme.colors.borderColor};
    `;
  }}
`;

export {OperationsList, EmptyMessage, CollapsablePanel};
