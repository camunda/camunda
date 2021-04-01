/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';

const TH = styled.th`
  font-style: italic;
  font-weight: normal;
  text-align: left;
  padding-left: 15px;
  height: 31px;
`;

type TRProps = {
  hasActiveOperation?: boolean;
};
const TR = styled.tr<TRProps>`
  ${({theme, hasActiveOperation}) => {
    const colors = theme.colors.variables.variablesTable.tr;
    const opacity = theme.opacity.variables.variablesTable.tr;

    return css`
      border-width: 1px 0;
      border-style: solid;
      border-color: ${colors.borderColor};

      &:first-child {
        border-top: none;
      }

      &:last-child {
        border-bottom: none;
      }

      > td:first-child {
        max-width: 226px;
        min-width: 226px;
        width: 226px;
      }

      ${hasActiveOperation
        ? css`
            background-color: ${colors.backgroundColor};
            opacity: ${opacity};
          `
        : ''};
    `;
  }}
`;

const Table = styled.table`
  width: 100%;
  margin-bottom: 3px;
  border-spacing: 0;
  border-collapse: collapse;
`;

export {TH, TR, Table};
