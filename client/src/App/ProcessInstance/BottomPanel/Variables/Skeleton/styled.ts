/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';

import {Table as DefaultTable} from '../VariablesTable';

const SkeletonTD = styled.td`
  height: 100%;
`;

const Table = styled(DefaultTable)`
  height: 100%;
  width: 100%;
  margin-bottom: 3px;
  border-spacing: 0;
  border-collapse: collapse;
`;

const THead = styled.thead`
  ${({theme}) => {
    const colors = theme.colors.variables.skeleton;

    return css`
      tr:first-child {
        position: absolute;
        width: 100%;
        top: 0;
        border-bottom: 1px solid ${theme.colors.borderColor};
        background: ${colors.backgroundColor};
        z-index: 2;
        border-top: none;
        height: 45px;

        > th {
          padding-top: 21px;
        }
        > th:first-child {
          min-width: 226px;
        }
      }
    `;
  }}
`;

export {SkeletonTD, Table, THead};
