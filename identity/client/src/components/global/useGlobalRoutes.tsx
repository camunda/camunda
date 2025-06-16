/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import useTranslate from "src/utility/localization";
import { useLocation } from "react-router-dom";
import Users from "src/pages/users";
import Groups from "src/pages/groups";
import Roles from "src/pages/roles";
import Tenants from "src/pages/tenants";
import Mappings from "src/pages/mappings";
import Authorizations from "src/pages/authorizations";
import { isOIDC, isInternalGroupsEnabled } from "src/configuration";
import { Paths } from "src/components/global/routePaths";

export const useGlobalRoutes = () => {
  const { t } = useTranslate();
  const { pathname } = useLocation();

  const OIDCDependentRoutes = isOIDC
    ? [
        {
          path: `${Paths.mappings()}/*`,
          key: Paths.mappings(),
          label: t("mappings"),
          element: <Mappings />,
        },
      ]
    : [
        {
          path: `${Paths.users()}/*`,
          key: Paths.users(),
          label: t("users"),
          element: <Users />,
        },
      ];

  const internalGroupsDependentRoutes = isInternalGroupsEnabled
    ? [
        {
          path: `${Paths.groups()}/*`,
          key: Paths.groups(),
          label: t("groups"),
          element: <Groups />,
        },
      ]
    : [];

  const routes = [
    ...OIDCDependentRoutes,
    ...internalGroupsDependentRoutes,
    {
      path: `${Paths.roles()}/*`,
      key: Paths.roles(),
      label: t("roles"),
      element: <Roles />,
    },
    {
      path: `${Paths.tenants()}/*`,
      key: Paths.tenants(),
      label: t("tenants"),
      element: <Tenants />,
    },
    {
      path: `${Paths.authorizations()}/*`,
      key: Paths.authorizations(),
      label: t("authorizations"),
      element: <Authorizations />,
    },
  ];

  return routes.map((route) => ({
    ...route,
    isCurrentPage: pathname.startsWith(route.key),
  }));
};
