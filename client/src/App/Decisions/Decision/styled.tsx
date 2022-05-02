/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';

const Container = styled.div`
  ${({theme}) => {
    return css`
      display: grid;
      grid-template-columns: 100%;
      grid-template-rows: 37px calc(100% - 37px);
      overflow: auto;
      height: 100%;
      position: relative;

      background: ${theme.colors.decisionViewer.background};
    `;
  }}
`;

export {Container};
