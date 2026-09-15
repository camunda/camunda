/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, lazy, Suspense } from "react";
import { ListPageFallback } from "src/components/fallbacksV2";
import PageRoutes from "src/components/router/PageRoutes";
import Detail from "src/pages/users/detailV2";

const List = lazy(() => import("./ListV2"));

const Users: FC = () => (
  <PageRoutes
    indexElement={
      <Suspense fallback={<ListPageFallback />}>
        <List />
      </Suspense>
    }
    detailElement={<Detail />}
  />
);

export default Users;
