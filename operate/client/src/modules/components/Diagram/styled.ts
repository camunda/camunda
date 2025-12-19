/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';

const Diagram = styled.div<{$isFullscreen?: boolean}>`
  height: 100%;
  width: 100%;
  position: relative;

  /* Apply canvas background color in fullscreen mode */
  ${({$isFullscreen}) =>
    $isFullscreen
      ? `
    background-color: var(--cds-layer-01);
  `
      : ''}
`;

const DiagramCanvas = styled.div`
  position: absolute;
  height: 100%;
  width: 100%;
  left: 0;
  top: 0;
  min-height: 200px;

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

  /* Position minimap above controls (bottom-right, above button row) */
  .djs-minimap {
    top: auto !important;
    bottom: calc(var(--cds-spacing-05) + 48px + var(--cds-spacing-02)) !important;
    right: var(--cds-spacing-05) !important;
    z-index: 10;
  }

  /* Make minimap smaller */
  .djs-minimap .map {
    width: 240px !important;
    height: 135px !important;
  }

  /* Hide the toggle text inside minimap */
  .djs-minimap .toggle:before {
    content: '' !important;
  }

  .djs-minimap:not(.open) .toggle {
    display: none;
  }

  /* Change viewport outline color to primary blue */
  .djs-minimap .viewport-dom {
    border-color: var(--cds-background-brand) !important;
  }
`;

export {Diagram, DiagramCanvas};
