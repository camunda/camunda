/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

const bpmnRendererColors = {
  outline: {
    fill: 'var(--cds-highlight)',
  },
  defaultFillColor: 'var(--cds-layer-01)',
  defaultStrokeColor: 'var(--cds-icon-secondary)',
  element: {
    text: 'var(--cds-text-primary)',
    background: {
      default: 'var(--cds-layer-01)',
    },
  },
};

const highlightedSequenceFlowsColor = 'var(--cds-link-inverse)';

export {bpmnRendererColors, highlightedSequenceFlowsColor};
