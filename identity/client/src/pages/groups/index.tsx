/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, lazy, Suspense } from "react";
import { ListPageFallback } from "src/components/fallbacks";
import { ListPageFallback as ListPageFallbackV2 } from "src/components/fallbacksV2";
import PageRoutes from "src/components/router/PageRoutes";
import { IS_NEW_DESIGN_SYSTEM_ENABLED } from "src/feature-flags";
import Detail from "src/pages/groups/detail";
import DetailV2 from "src/pages/groups/detailV2";

const List = lazy(() =>
  IS_NEW_DESIGN_SYSTEM_ENABLED ? import("./ListV2") : import("./List"),
);

type GroupsProps = {
  isOIDC: boolean;
};

const Groups: FC<GroupsProps> = ({ isOIDC }) => (
  <PageRoutes
    indexElement={
      <Suspense
        fallback={
          IS_NEW_DESIGN_SYSTEM_ENABLED ? (
            <ListPageFallbackV2 />
          ) : (
            <ListPageFallback />
          )
        }
      >
        <List />
      </Suspense>
    }
    detailElement={
      IS_NEW_DESIGN_SYSTEM_ENABLED ? (
        <DetailV2 isOIDC={isOIDC} />
      ) : (
        <Detail isOIDC={isOIDC} />
      )
    }
  />
);

export default Groups;
