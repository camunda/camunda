/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const bpmnRendererColors = {
  outline: {
    fill: 'var(--cds-highlight)',
  },
  defaultFillColor: 'var(--cds-layer)',
  defaultStrokeColor: 'var(--cds-icon-secondary)',
  element: {
    text: 'var(--cds-text-primary)',
    background: {
      default: 'var(--cds-layer)',
    },
  },
};

const highlightedSequenceFlowsColor = 'var(--cds-link-inverse)';

export {bpmnRendererColors, highlightedSequenceFlowsColor};
