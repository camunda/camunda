/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import Lazy from "src/components/router/Lazy";
import PageRoutes from "src/components/router/PageRoutes";
import type {
  PermissionType,
  ResourceType,
} from "@camunda/camunda-api-zod-schemas/8.10";

type AuthorizationsProps = {
  isOIDC: boolean;
  isCamundaGroupsEnabled: boolean;
  isTenantsApiEnabled: boolean;
  resourcePermissions: Record<ResourceType, PermissionType[]>;
};

const Authorizations: FC<AuthorizationsProps> = ({
  isOIDC,
  isCamundaGroupsEnabled,
  isTenantsApiEnabled,
  resourcePermissions,
}) => (
  <PageRoutes
    indexElement={
      <Lazy
        load={() => import("./List")}
        elementProps={{
          isOIDC,
          isCamundaGroupsEnabled,
          isTenantsApiEnabled,
          resourcePermissions,
        }}
      />
    }
    detailElement={
      <Lazy
        load={() => import("./List")}
        elementProps={{
          isOIDC,
          isCamundaGroupsEnabled,
          isTenantsApiEnabled,
          resourcePermissions,
        }}
      />
    }
  />
);

export default Authorizations;
