/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';

import {themed, themeStyle, Colors} from 'modules/theme';

export const Diagram = styled.div`
  flex-grow: 1;
  position: relative;
`;

export const DiagramCanvas = themed(styled.div`
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
    stroke: ${Colors.selections};
  }

  .op-selected .djs-outline {
    stroke-width: 3px;
    stroke: ${Colors.selections};
    fill: ${themeStyle({
      dark: 'rgba(58, 82, 125, 0.5)',
      light: 'rgba(189, 212, 253, 0.5)',
    })} !important;
  }
`);
