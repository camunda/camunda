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
import GlobalTaskListeners from "src/pages/global-task-listeners";
import GlobalExecutionListeners from "src/pages/global-execution-listeners";
import {
  isCamundaGroupsEnabled,
  isOIDC,
  isSaaS,
  isTenantsApiEnabled,
} from "src/configuration";
import { Paths } from "src/components/global/routePaths";
import { JSX } from "react";

export type RouteEntry = {
  path: string;
  key: string;
  label: string;
  element: JSX.Element;
  isCurrentPage: boolean;
};

export type GroupEntry = {
  key: string;
  label: string;
  isCurrentPage: boolean;
  subElements: RouteEntry[];
};

export type GlobalRoute = RouteEntry | GroupEntry;

export const useGlobalRoutes = (): GlobalRoute[] => {
  const { t } = useTranslate();
  const { pathname } = useLocation();

  const route = (
    key: string,
    label: string,
    element: JSX.Element,
  ): RouteEntry => ({
    path: `${key}/*`,
    key,
    label,
    element,
    isCurrentPage: pathname.startsWith(key),
  });

  const OIDCDependentRoutes: RouteEntry[] = !isOIDC
    ? [route(Paths.users(), t("users"), <Users />)]
    : !isSaaS
      ? [route(Paths.mappingRules(), t("mappingRules"), <MappingRules />)]
      : [];

  const camundaGroupsDependentRoutes: RouteEntry[] = isCamundaGroupsEnabled
    ? [route(Paths.groups(), t("groups"), <Groups />)]
    : [];

  const tenantsDependentRoutes: RouteEntry[] = isTenantsApiEnabled
    ? [route(Paths.tenants(), t("tenants"), <Tenants />)]
    : [];

  const listenersGroup: GroupEntry = {
    key: Paths.listeners(),
    label: t("listeners"),
    isCurrentPage: pathname.startsWith(Paths.listeners()),
    subElements: [
      route(
        Paths.globalTaskListeners(),
        t("taskListeners"),
        <GlobalTaskListeners />,
      ),
      route(
        Paths.globalExecutionListeners(),
        t("executionListeners"),
        <GlobalExecutionListeners />,
      ),
    ],
  };

  return [
    ...OIDCDependentRoutes,
    ...camundaGroupsDependentRoutes,
    route(Paths.roles(), t("roles"), <Roles />),
    ...tenantsDependentRoutes,
    route(Paths.authorizations(), t("authorizations"), <Authorizations />),
    listenersGroup,
    route(
      Paths.clusterVariables(),
      t("clusterVariables"),
      <ClusterVariables />,
    ),
    route(Paths.operationsLog(), t("operationsLog"), <OperationsLog />),
  ];
};
