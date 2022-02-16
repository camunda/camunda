/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
import BasePanelHeader from 'modules/components/Panel/PanelHeader';

const Container = styled.div`
  ${({theme}) => {
    const colors = theme.colors.modules.drdDiagram;

    return css`
      display: grid;
      grid-template-rows: 56px 1fr;
      height: 100%;
      width: 100%;
      background: ${colors.background};

      .dmn-drd-container .djs-visual rect {
        stroke: ${colors.stroke} !important;
        fill: ${colors.background} !important;
      }

      .dmn-drd-container .djs-label {
        fill: ${colors.text} !important;
      }

      .dmn-drd-container .djs-connection polyline {
        stroke: ${colors.stroke} !important;
      }

      marker#information-requirement-end {
        fill: ${colors.stroke} !important;
      }
    `;
  }}
`;

const PanelHeader = styled(BasePanelHeader)`
  display: flex;
  justify-content: space-between;
  align-items: end;
  padding: 18px 20px;
`;

export {Container, PanelHeader};
