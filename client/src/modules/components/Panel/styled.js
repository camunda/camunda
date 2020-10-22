/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
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
      border: solid 1px ${colors.borderColor};
      border-bottom: none;
      background-color: ${colors.backgroundColor};
    `;
  }}
`;

export {Panel};
