/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  Bot,
  File,
  ListChecks,
  Package,
  Settings,
  ShieldCheck,
  User,
  UserCog,
  Users,
  Waypoints,
  Zap,
} from "lucide-react";
import type { NavIcon, SidebarNode } from "@camunda/design-system";

import { useGlobalRoutes } from "src/components/global/useGlobalRoutes";
import { Paths } from "src/components/global/routePaths";

const ROUTE_ICONS: Record<string, NavIcon> = {
  [Paths.users()]: User,
  [Paths.groups()]: Users,
  [Paths.roles()]: UserCog,
  [Paths.tenants()]: Package,
  [Paths.mappingRules()]: Waypoints,
  [Paths.authorizations()]: ShieldCheck,
  [Paths.clusterVariables()]: Settings,
  [Paths.operationsLog()]: ListChecks,
  [Paths.globalTaskListeners()]: Zap,
  [Paths.mcpProcesses()]: Bot,
};

export function useSidebarChildren(hideNavLinks: boolean): SidebarNode[] {
  const routes = useGlobalRoutes();

  if (hideNavLinks) {
    return [];
  }

  return routes.map((route) => ({
    type: "item" as const,
    key: route.key,
    label: route.label,
    icon: ROUTE_ICONS[route.key] ?? File,
    isActive: route.isCurrentPage,
    linkProps: { to: route.key },
  }));
}
