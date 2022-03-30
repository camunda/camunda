/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {HEADER_HEIGHT} from 'modules/constants';
import styled, {css} from 'styled-components';

const Container = styled.div`
  display: flex;
  position: absolute;
  height: calc(100vh - ${HEADER_HEIGHT}px);
  right: 0;
`;

const Handle = styled.div`
  cursor: ew-resize;
  position: absolute;
  left: -5px;
  width: 10px;
  height: 100%;
`;

const Panel = styled.div`
  ${({theme}) => {
    const colors = theme.colors.drdPanel;

    return css`
      box-shadow: 0 2px 4px 0 ${colors.boxShadow};
      width: 540px;
      border-left: solid 1px ${colors.borderColor};

      &.resizing {
        border-left-color: ${theme.colors.selections};
      }
    `;
  }}
`;

export {Container, Handle, Panel};
