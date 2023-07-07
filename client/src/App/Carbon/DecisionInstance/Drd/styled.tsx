/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {
  PAGE_TOP_PADDING,
  COLLAPSABLE_PANEL_HEADER_HEIGHT,
} from 'modules/constants';
import {PanelHeader as BasePanelHeader} from 'modules/components/Carbon/PanelHeader';

const PanelHeader = styled(BasePanelHeader)`
  padding-right: 0;
`;

const Container = styled.div`
  ${({theme}) => {
    const colors = theme.colors.modules.diagram.element;

    return css`
      display: grid;
      grid-template-rows: ${COLLAPSABLE_PANEL_HEADER_HEIGHT} 1fr;
      height: calc(100vh - ${PAGE_TOP_PADDING}px);
      position: relative;
      width: 100%;
      background: var(--cds-layer);

      .dmn-drd-container .djs-visual rect {
        stroke: ${colors.border} !important;
        fill: ${colors.background.default} !important;
      }

      .dmn-drd-container .djs-label {
        fill: ${colors.text} !important;
      }

      .dmn-drd-container .djs-connection path {
        stroke: ${colors.border} !important;
      }

      marker#information-requirement-end {
        fill: ${colors.border} !important;
      }

      .ope-selectable {
        cursor: pointer;

        &.hover .djs-outline {
          stroke: ${colors.outline};
          stroke-width: 2px;
        }
      }

      .ope-selected {
        .djs-outline {
          stroke: ${colors.outline};
          stroke-width: 2px;
        }

        .djs-visual rect {
          fill: ${colors.background.selected} !important;
        }
      }
    `;
  }}
`;

export {PanelHeader, Container};
