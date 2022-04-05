/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import Table from 'modules/components/Table';

const TR = styled(Table.TR)`
  line-height: 36px;
  &:first-child {
    border-top-style: hidden;
  }
`;

const TD = styled(Table.TD)`
  ${({theme}) => {
    return css`
      color: ${theme.colors.text01};
      &:first-child {
        padding-left: 19px;
      }
    `;
  }}
`;

export {TR, TD};
