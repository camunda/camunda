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
import type {
  PermissionType,
  ResourceType,
} from "@camunda/camunda-api-zod-schemas/8.10";
import { IS_NEW_DESIGN_SYSTEM_ENABLED } from "src/feature-flags";

const List = lazy(() =>
  IS_NEW_DESIGN_SYSTEM_ENABLED ? import("./ListV2") : import("./List"),
);

type AuthorizationsProps = {
  isOIDC: boolean;
  isCamundaGroupsEnabled: boolean;
  isTenantsApiEnabled: boolean;
  resourcePermissions: Record<ResourceType, PermissionType[]>;
  defaultRoleIds: string[];
};

const Authorizations: FC<AuthorizationsProps> = ({
  isOIDC,
  isCamundaGroupsEnabled,
  isTenantsApiEnabled,
  resourcePermissions,
  defaultRoleIds,
}) => {
  const list = (
    <Suspense
      fallback={
        IS_NEW_DESIGN_SYSTEM_ENABLED ? (
          <ListPageFallbackV2 />
        ) : (
          <ListPageFallback />
        )
      }
    >
      <List
        isOIDC={isOIDC}
        isCamundaGroupsEnabled={isCamundaGroupsEnabled}
        isTenantsApiEnabled={isTenantsApiEnabled}
        resourcePermissions={resourcePermissions}
        defaultRoleIds={defaultRoleIds}
      />
    </Suspense>
  );

  return <PageRoutes indexElement={list} detailElement={list} />;
};

export default Authorizations;
