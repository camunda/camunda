/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';

const Diagram = styled.div`
  height: 100%;
  position: relative;
`;

const DiagramCanvas = styled.div`
  ${({theme}) => {
    const colors = theme.colors.modules.diagram.outline;

    return css`
      position: absolute;
      height: 100%;
      width: 100%;
      left: 0;
      top: 0;

      .op-selectable:hover {
        cursor: pointer;
      }

      .op-selectable:hover .djs-outline {
        stroke-width: 3px;
        stroke: ${theme.colors.selections};
      }

      .op-selected .djs-outline {
        stroke-width: 3px;
        stroke: ${theme.colors.selections};
        fill: ${colors.fill} !important;
      }
    `;
  }}
`;

export {Diagram, DiagramCanvas};
