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
import MappingRules from "src/pages/mapping-rules";
import Authorizations from "src/pages/authorizations";
import ClusterVariables from "src/pages/cluster-variables";
import OperationsLog from "src/pages/operations-log";
import TaskListeners from "src/pages/task-listeners";
import {
  isCamundaGroupsEnabled,
  isOIDC,
  isSaaS,
  isTenantsApiEnabled,
} from "src/configuration";
import { Paths } from "src/components/global/routePaths";

export const useGlobalRoutes = () => {
  const { t } = useTranslate();
  const { pathname } = useLocation();

  const OIDCDependentRoutes = !isOIDC
    ? [
        {
          path: `${Paths.users()}/*`,
          key: Paths.users(),
          label: t("users"),
          element: <Users />,
        },
      ]
    : !isSaaS
      ? [
          {
            path: `${Paths.mappingRules()}/*`,
            key: Paths.mappingRules(),
            label: t("mappingRules"),
            element: <MappingRules />,
          },
        ]
      : [];

  const camundaGroupsDependentRoutes = isCamundaGroupsEnabled
    ? [
        {
          path: `${Paths.groups()}/*`,
          key: Paths.groups(),
          label: t("groups"),
          element: <Groups />,
        },
      ]
    : [];

  const tenantsDependentRoutes = isTenantsApiEnabled
    ? [
        {
          path: `${Paths.tenants()}/*`,
          key: Paths.tenants(),
          label: t("tenants"),
          element: <Tenants />,
        },
      ]
    : [];

  const routes = [
    ...OIDCDependentRoutes,
    ...camundaGroupsDependentRoutes,
    {
      path: `${Paths.roles()}/*`,
      key: Paths.roles(),
      label: t("roles"),
      element: <Roles />,
    },
    ...tenantsDependentRoutes,
    {
      path: `${Paths.authorizations()}/*`,
      key: Paths.authorizations(),
      label: t("authorizations"),
      element: <Authorizations />,
    },
    {
      path: `${Paths.taskListeners()}/*`,
      key: Paths.taskListeners(),
      label: t("taskListeners"),
      element: <TaskListeners />,
    },
    {
      path: `${Paths.clusterVariables()}/*`,
      key: Paths.clusterVariables(),
      label: t("clusterVariables"),
      element: <ClusterVariables />,
    },
    {
      path: `${Paths.operationsLog()}/*`,
      key: Paths.operationsLog(),
      label: t("operationsLog"),
      element: <OperationsLog />,
    },
  ];

  return routes.map((route) => ({
    ...route,
    isCurrentPage: pathname.startsWith(route.key),
  }));
};
