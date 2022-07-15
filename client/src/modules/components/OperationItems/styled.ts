/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';

const Ul = styled.ul`
  ${({theme}) => {
    const shadow = theme.shadows.modules.operationItems.ul;

    return css`
      display: inline-flex;
      flex-direction: row;
      border-radius: 12px;
      box-shadow: ${shadow};
    `;
  }}
`;

export {Ul};
