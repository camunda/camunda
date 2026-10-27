/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, lazy, Suspense } from "react";
import { ListPageFallback } from "src/components/fallbacks";
import PageRoutes from "src/components/router/PageRoutes";
import Detail from "src/pages/groups/detail";

const List = lazy(() => import("./List"));

type GroupsProps = {
  isOIDC: boolean;
};

const Groups: FC<GroupsProps> = ({ isOIDC }) => (
  <PageRoutes
    indexElement={
      <Suspense fallback={<ListPageFallback />}>
        <List />
      </Suspense>
    }
    detailElement={<Detail isOIDC={isOIDC} />}
  />
);

export default Groups;
