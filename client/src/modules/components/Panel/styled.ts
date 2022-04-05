/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';

const Panel = styled.div`
  ${({theme}) => {
    const colors = theme.colors.modules.panel;

    return css`
      position: relative;
      display: flex;
      flex-direction: column;
      width: 100%;
      background-color: ${colors.backgroundColor};
    `;
  }}
`;

export {Panel};
