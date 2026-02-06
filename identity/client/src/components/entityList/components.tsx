/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from "styled-components";
import { TableContainer } from "@carbon/react";
import { spacing04 } from "@carbon/elements";

export const DocumentationDescription = styled.p`
  margin-top: ${spacing04};
  max-width: none;
  text-align: left;
`;

export const StyledTableContainer = styled(TableContainer)`
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  overflow: hidden;

  .cds--skeleton {
    padding: 0;
  }

  &.no-header .cds--data-table-header {
    display: none !important;
  }

  .cds--data-table-container,
  .cds--data-table-content {
    overflow: visible;
  }

  .cds--data-table thead {
    position: sticky;
    top: 0;
    z-index: 2;
  }

  .cds--data-table thead th {
    position: sticky;
    top: 0;
    z-index: 3;
  }
`;

export const TableContentWrapper = styled.div`
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
  min-height: 0;
  overflow-x: hidden;
`;

export const PaginationWrapper = styled.div`
  flex-shrink: 0;
  background-color: var(--cds-layer-01);
  padding-bottom: var(--cds-spacing-03);
  z-index: 1;
`;
