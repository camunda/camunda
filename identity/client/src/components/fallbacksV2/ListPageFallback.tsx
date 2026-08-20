/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { DataTableSkeleton } from "@camunda/design-system/carbon-compat";
import Page from "src/components/layoutV2/Page";

type ListPageFallbackProps = { columns?: number };

const ListPageFallback: FC<ListPageFallbackProps> = ({ columns = 2 }) => (
  <Page>
    <DataTableSkeleton columnCount={columns} />
  </Page>
);

export default ListPageFallback;
