/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';

const Container = styled.main`
  display: flex;
  flex-direction: column;
  height: 100%;
  width: 100%;
`;

const PanelContainer = styled.div`
  overflow: hidden;
  height: 100%;
`;

const BottomPanel = styled.div`
  ${({theme}) => {
    return css`
      border-top: 1px solid ${theme.colors.borderColor};
      display: flex;
      flex-direction: row;
      height: 100%;
    `;
  }}
`;

export {Container, PanelContainer, BottomPanel};
