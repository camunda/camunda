/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';

const Diagram = styled.div`
  height: 100%;
  position: relative;
`;

const DiagramCanvas = styled.div`
  ${({theme}) => {
    const elementColors = theme.colors.modules.diagram.element;

    return css`
      position: absolute;
      height: 100%;
      width: 100%;
      left: 0;
      top: 0;

      .op-selectable:hover {
        cursor: pointer;
      }

      .op-selectable:hover .djs-outline,
      .op-selected-frame .djs-outline {
        stroke-width: 2px;
        stroke: ${elementColors.outline};
      }

      .op-non-selectable {
        cursor: not-allowed;
      }

      .op-selected .djs-visual {
        rect,
        circle,
        polygon {
          fill: ${elementColors.background.selected} !important;
        }
      }
    `;
  }}
`;

export {Diagram, DiagramCanvas};
