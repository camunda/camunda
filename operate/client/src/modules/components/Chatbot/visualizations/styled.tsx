/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';

const ChartContainer = styled.div`
  margin-top: var(--cds-spacing-04);
  padding: var(--cds-spacing-04);
  background: var(--cds-field);
  border-radius: 8px;
  width: 100%;
  overflow: hidden;

  h4 {
    margin: 0 0 var(--cds-spacing-03) 0;
    font-size: var(--cds-heading-compact-01-font-size);
    font-weight: 600;
    color: var(--cds-text-primary);
  }

  // Carbon Charts overrides for compact display
  .cds--cc--chart-wrapper {
    min-height: 250px;
  }

  .cds--cc--chart-svg {
    overflow: visible;
  }

  // Adjust axis labels for small space
  .cds--cc--axis-title {
    font-size: 11px;
  }

  .cds--cc--axis text {
    font-size: 10px;
  }

  // Tooltip styling
  .cds--cc--tooltip {
    max-width: 200px;
  }
`;

export {ChartContainer};
