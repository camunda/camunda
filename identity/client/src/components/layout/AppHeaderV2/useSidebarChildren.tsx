/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  Connect,
  Document,
  Enterprise,
  Folder,
  Group,
  License,
  ListChecked,
  Settings,
  User,
  UserAccess,
} from "@carbon/react/icons";
import type { CarbonIconType } from "@carbon/icons-react";
import type { SidebarNodeDescriptor } from "@camunda/camunda-composite-components";

import { useGlobalRoutes } from "src/components/global/useGlobalRoutes";
import { Paths } from "src/components/global/routePaths";

const ROUTE_ICONS: Record<string, CarbonIconType> = {
  [Paths.users()]: User,
  [Paths.groups()]: Group,
  [Paths.roles()]: UserAccess,
  [Paths.tenants()]: Enterprise,
  [Paths.mappingRules()]: Connect,
  [Paths.authorizations()]: License,
  [Paths.clusterVariables()]: Settings,
  [Paths.operationsLog()]: ListChecked,
  [Paths.globalTaskListeners()]: Folder,
  [Paths.mcpProcesses()]: Folder,
};

export function useSidebarChildren(
  hideNavLinks: boolean,
): SidebarNodeDescriptor[] {
  const routes = useGlobalRoutes();

  if (hideNavLinks) {
    return [];
  }

  return routes.map((route) => ({
    type: "item" as const,
    key: route.key,
    label: route.label,
    icon: ROUTE_ICONS[route.key] ?? Document,
    isActive: route.isCurrentPage,
    linkProps: { to: route.key },
  }));
}
