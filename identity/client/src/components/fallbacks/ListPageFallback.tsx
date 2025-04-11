/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { DataTableSkeleton } from "@carbon/react";
import Page from "src/components/layout/Page";
import styled from "styled-components";

const StyledPage = styled(Page)`
  .cds--data-table-header__description {
    display: none;
  }
`;

type ListPageFallbackProps = { columns?: number };

const ListPageFallback: FC<ListPageFallbackProps> = ({ columns = 2 }) => (
  <StyledPage>
    <DataTableSkeleton columnCount={columns} />
  </StyledPage>
);

export default ListPageFallback;
