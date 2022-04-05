/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import styled, {css} from 'styled-components';

import Spinner from 'modules/components/Spinner';

const OperationSpinner = styled<
  React.FC<{isSelected?: boolean; title?: string}>
>(Spinner)`
  ${({theme, isSelected = false}) => {
    const colors = theme.colors.modules.operations;

    return css`
      margin: 0 5px;
      width: 14px;
      height: 14px;
      border: 2px solid
        ${isSelected ? colors.selected.borderColor : colors.default.borderColor};
      border-right-color: transparent;
    `;
  }}
`;

export {OperationSpinner};
