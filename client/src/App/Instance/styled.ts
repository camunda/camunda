/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';

const Container = styled.main`
  display: flex;
  flex-direction: column;
  height: 100%;
`;

const PanelContainer = styled.div`
  overflow: hidden;
  height: 100%;
`;

const Content = styled.div`
  display: flex;
  height: 100%;
`;

const BottomPanel = styled.div`
  ${({theme}) => {
    const colors = theme.colors.bottomPanel;
    return css`
      border-top: 1px solid ${colors.borderColor};
      display: flex;
      flex-direction: column;
      height: 100%;
    `;
  }}
`;

export {Container, PanelContainer, Content, BottomPanel};
