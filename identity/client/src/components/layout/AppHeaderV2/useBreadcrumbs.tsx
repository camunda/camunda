/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { useMemo, type ReactNode } from "react";
import { useLocation } from "react-router-dom";
import {
  preview_useClusterWebappBreadcrumbs as useClusterWebappBreadcrumbs,
  type BreadcrumbDescriptor,
} from "@camunda/camunda-composite-components";
import {
  ClusterIcon,
  NavBreadcrumbSwitcher,
  OrganisationIcon,
  camundaAppIcons,
  type CamundaAppKey,
  type NavBreadcrumbDescriptor,
  type NavIcon,
} from "@camunda/design-system";

import useTranslate from "src/utility/localization";

import { ForwardRefLink } from "./ForwardRefLink";

/**
 * Icons come from the design system's registry rather than from the crumb data:
 * C3 types its `icon` for Carbon's sizing contract, and the registry is the
 * agreed glyph per context across the Camunda apps.
 */
function crumbIcon(key: string): NavIcon {
  if (key === "org") return OrganisationIcon;
  if (key === "cluster") return ClusterIcon;
  return camundaAppIcons.identity;
}

function appIcon(key: string): NavIcon | undefined {
  return Object.prototype.hasOwnProperty.call(camundaAppIcons, key)
    ? camundaAppIcons[key as CamundaAppKey]
    : undefined;
}

function mapBreadcrumb(
  breadcrumb: BreadcrumbDescriptor,
): NavBreadcrumbDescriptor {
  return {
    key: breadcrumb.key,
    label: breadcrumb.label,
    icon: crumbIcon(breadcrumb.key),
    onClick: breadcrumb.onClick,
    linkProps:
      breadcrumb.key === "app"
        ? (breadcrumb.linkProps ?? { to: "/" })
        : breadcrumb.linkProps,
    actions: breadcrumb.actions,
    dropdownTitle: breadcrumb.dropdownTitle,
    dropdownAriaLabel: breadcrumb.dropdownAriaLabel,
    dropdownItems: breadcrumb.dropdownItems?.map((item) => ({
      key: item.key,
      label: item.label,
      icon:
        breadcrumb.key === "app"
          ? appIcon(item.key)
          : crumbIcon(breadcrumb.key),
      isSelected: item.isSelected,
      onClick: item.onClick,
      linkProps: item.linkProps,
      trailingElement: item.trailingElement,
    })),
    trailingElement: breadcrumb.trailingElement,
    menuElement: breadcrumb.menuElement,
  };
}

/**
 * The org / cluster / app context chain for the header.
 *
 * C3 stays the data source — the design system ships the rendering half only —
 * and its descriptors map onto `NavBreadcrumbDescriptor` field for field, so no
 * adapter beyond the icon and app-root defaults above is needed.
 */
export function useBreadcrumbs(): ReactNode | undefined {
  const { pathname, search } = useLocation();
  const { t } = useTranslate("navigation");
  const breadcrumbs = useClusterWebappBreadcrumbs({ currentApp: "admin" });
  const items = useMemo(() => breadcrumbs.map(mapBreadcrumb), [breadcrumbs]);

  if (items.length === 0) return undefined;

  return (
    <NavBreadcrumbSwitcher
      items={items}
      activeItemKey={`${pathname}${search}`}
      linkComponent={ForwardRefLink}
      aria-label={t("breadcrumb")}
    />
  );
}
