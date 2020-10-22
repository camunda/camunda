/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';

import Spinner from 'modules/components/Spinner';

const OperationSpinner = styled(Spinner)`
  ${({theme, selected}) => {
    const colors = theme.colors.modules.operations;

    return css`
      margin: 0 5px;
      width: 14px;
      height: 14px;
      border: 2px solid
        ${selected ? colors.selected.borderColor : colors.default.borderColor};
      border-right-color: transparent;
    `;
  }}
`;

export {OperationSpinner};
