/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
import {EmptyMessage} from 'modules/components/EmptyMessage';

const DiagramEmptyMessage = styled(EmptyMessage)`
  position: absolute;
  height: 100%;
  width: 100%;
  left: 0;
  top: 0;
`;

const DiagramContainer = styled.div`
  position: relative;
  display: flex;
  flex-direction: column;
  height: 100%;
`;

const Container = styled.div`
  ${({theme}) => {
    const colors = theme.colors.modules.panel;

    return css`
      display: flex;
      flex-direction: column;
      background-color: ${colors.backgroundColor};
      height: 100%;
    `;
  }}
`;

export {DiagramEmptyMessage, DiagramContainer, Container};
