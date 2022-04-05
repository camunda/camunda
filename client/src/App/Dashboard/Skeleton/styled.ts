/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';

const Container = styled.div`
  display: flex;
  flex-direction: column;
`;

const Block = styled.div`
  ${({theme}) => {
    const colors = theme.colors.dashboard.skeleton.block;
    const opacity = theme.opacity.dashboard.skeleton.block;

    return css`
      margin: 15px 19px 19px 33px;
      height: 21px;
      flex-grow: 1;
      background: ${colors.backgroundColor};
      opacity: ${opacity};
    `;
  }}
`;

export {Container, Block};
