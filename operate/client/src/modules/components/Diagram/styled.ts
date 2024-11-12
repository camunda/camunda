/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';

const Diagram = styled.div`
  height: 100%;
  width: 100%;
  position: relative;
`;

const DiagramCanvas = styled.div`
  position: absolute;
  height: 100%;
  width: 100%;
  left: 0;
  top: 0;

  .op-highlighted.djs-shape .djs-visual > :nth-child(1) {
    stroke: var(--cds-background-brand) !important;
  }

  .op-selectable:hover {
    cursor: pointer;
  }

  .op-selectable:hover .djs-outline,
  .op-selected-frame .djs-outline {
    stroke-width: 2px;
    stroke: var(--cds-link-inverse);
  }

  .op-non-selectable {
    cursor: not-allowed;
  }

  .op-selected .djs-visual {
    rect,
    circle,
    polygon {
      fill: var(--cds-highlight) !important;
    }
  }
`;

export {Diagram, DiagramCanvas};
