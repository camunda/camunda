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
import Detail from "src/pages/roles/detail";
import DetailV2 from "src/pages/roles/detailV2";

const List = lazy(() =>
  IS_NEW_DESIGN_SYSTEM_ENABLED ? import("./ListV2") : import("./List"),
);

type RolesProps = {
  isOIDC: boolean;
  isCamundaGroupsEnabled: boolean;
  defaultRoleIds: string[];
};

const Roles: FC<RolesProps> = ({
  isOIDC,
  isCamundaGroupsEnabled,
  defaultRoleIds,
}) => (
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
        <List defaultRoleIds={defaultRoleIds} />
      </Suspense>
    }
    detailElement={
      IS_NEW_DESIGN_SYSTEM_ENABLED ? (
        <DetailV2
          isOIDC={isOIDC}
          isCamundaGroupsEnabled={isCamundaGroupsEnabled}
          defaultRoleIds={defaultRoleIds}
        />
      ) : (
        <Detail
          isOIDC={isOIDC}
          isCamundaGroupsEnabled={isCamundaGroupsEnabled}
          defaultRoleIds={defaultRoleIds}
        />
      )
    }
  />
);

export default Roles;
