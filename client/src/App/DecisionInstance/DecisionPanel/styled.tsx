/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
const Container = styled.div`
  ${({theme}) => {
    const colors = theme.colors.decisionPanel;
    return css`
      background: ${colors.background};
      padding: 30px 20px;

      .powered-by {
        display: none;
      }

      .dmn-decision-table-container {
        --table-head-clause-color: ${theme.colors.text01};
        --table-head-variable-color: ${colors.text};
        --table-color: ${colors.text};
        --table-cell-color: ${colors.text};
        --decision-table-color: ${colors.text};
        --decision-table-properties-color: ${colors.text};
        --table-head-border-color: ${colors.border};
        --table-cell-border-color: ${colors.border};
        --decision-table-background-color: ${colors.background};
        --table-row-alternative-background-color: ${colors.background};

        .decision-table-properties {
          border-width: 2px 2px 1px 2px;
        }

        .tjs-table-container {
          border-width: 2px 2px 1px 2px;
        }
      }
    `;
  }}
`;

export {Container};
