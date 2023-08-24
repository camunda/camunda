/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {StructuredList as BaseStructuredList} from 'modules/components/StructuredList';
import {styles} from '@carbon/elements';

const VariablesContent = styled.div`
  position: absolute;
  width: 100%;
  height: 100%;
  top: 0;
  left: 0;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
`;

const VariableName = styled.div`
  ${styles.bodyShort01};
  margin: var(--cds-spacing-02) 0;
  text-overflow: ellipsis;
  overflow: hidden;
  white-space: nowrap;
`;

type VariableValueProps = {$hasBackdrop?: boolean};
const VariableValue = styled.div<VariableValueProps>`
  ${({$hasBackdrop}) => {
    return css`
      ${styles.bodyShort01};
      margin: var(--cds-spacing-02) 0;
      max-height: 78px;
      overflow-y: auto;
      overflow-wrap: break-word;
      ${$hasBackdrop &&
      css`
        position: relative;
      `}
    `;
  }}
`;
const StructuredList = styled(BaseStructuredList)`
  padding: var(--cds-spacing-05);
  [role='table'] {
    table-layout: fixed;
  }
`;

const EmptyMessageWrapper = styled.div`
  display: flex;
  height: 100%;
  justify-content: center;
  align-items: center;
`;

export {
  VariablesContent,
  VariableName,
  VariableValue,
  StructuredList,
  EmptyMessageWrapper,
};
