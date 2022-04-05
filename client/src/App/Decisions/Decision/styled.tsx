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
      background: ${theme.colors.decisionViewer.background};
      overflow: auto;
      height: 100%;
      position: relative;
    `;
  }}
`;

export {Container};
