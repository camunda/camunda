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
import {
  C3LicenseTag,
  C3ThemeSelector,
  preview_C3NavigationV2 as C3NavigationV2,
  preview_useC3NavigationV2 as useC3NavigationV2,
  preview_useCamundaTools as useCamundaTools,
  preview_useClusterWebappBreadcrumbs as useClusterWebappBreadcrumbs,
  type SidebarNodeDescriptor,
} from "@camunda/camunda-composite-components";
import type { License as LicenseDto } from "@camunda/camunda-api-zod-schemas/8.10";
import { observer } from "mobx-react-lite";
import { Link } from "react-router-dom";
import { useState } from "react";

import { useGlobalRoutes } from "src/components/global/useGlobalRoutes";
import { Paths } from "src/components/global/routePaths";
import { useApi } from "src/utility/api";
import { checkLicense } from "src/utility/api/headers";
import { getAuthentication } from "src/utility/api/authentication";
import { logout } from "src/utility/auth";
import { useNotifications } from "src/components/notifications";
import useTranslate from "src/utility/localization";
import { themeStore, isThemeOption } from "src/common/theme/theme";
import { isSaaS } from "src/configuration";

const SKIP_TO_CONTENT_TARGET_ID = "main-content";
const LOGOUT_DELAY = 1000;

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

const AppHeaderV2 = observer(
  ({ hideNavLinks = false }: { hideNavLinks?: boolean }) => {
    const routes = useGlobalRoutes();
    const { data: license } = useApi(checkLicense);
    const { data: camundaUser } = useApi(getAuthentication);
    const { enqueueNotification } = useNotifications();
    const { t } = useTranslate("authentication");
    const { t: tNav } = useTranslate("navigation");
    const [activeKey] = useState<string>(() => {
      const found = routes.find((r) => r.isCurrentPage);
      return found?.key ?? "";
    });

    const logoutWithNotification = async () => {
      enqueueNotification({
        kind: "info",
        title: t("logOut"),
        subtitle: t("beingLoggedOut"),
      });
      return setTimeout(logout, LOGOUT_DELAY);
    };

    const breadcrumbs = useClusterWebappBreadcrumbs({ currentApp: "admin" });

    const { tools, ToolsProvider } = useCamundaTools({
      notifications: isSaaS
        ? {
            ariaLabel: tNav("notifications"),
            title: tNav("notifications"),
            labels: {
              dismissAll: tNav("notificationsDismissAll"),
              emptyTitle: tNav("notificationsEmptyTitle"),
              emptyDescription: tNav("notificationsEmptyDescription"),
            },
          }
        : undefined,
      info: {
        ariaLabel: tNav("info"),
        title: tNav("info"),
        elements: [
          {
            key: "documentation",
            label: tNav("infoDocumentation"),
            onClick: () => window.open("https://docs.camunda.io/", "_blank"),
          },
          {
            key: "academy",
            label: tNav("infoAcademy"),
            onClick: () =>
              window.open("https://academy.camunda.com/", "_blank"),
          },
          {
            key: "communityForum",
            label: tNav("infoCommunityForum"),
            onClick: () => window.open("https://forum.camunda.io", "_blank"),
          },
        ],
      },
      user: {
        ariaLabel: tNav("settings"),
        title: tNav("settings"),
        version: import.meta.env.VITE_APP_VERSION,
        name: camundaUser?.displayName ?? "",
        email: camundaUser?.email ?? "",
        onLogout: camundaUser?.canLogout ? logoutWithNotification : undefined,
        labels: {
          logOut: t("logOut"),
          termsOfUse: tNav("termsOfUse"),
          privacyPolicy: tNav("privacyPolicy"),
          imprint: tNav("imprint"),
        },
        customSection: (
          <C3ThemeSelector
            currentTheme={themeStore.selectedTheme}
            onChange={(theme) => {
              if (isThemeOption(theme)) {
                themeStore.changeTheme(theme);
              }
            }}
            labels={{
              legend: tNav("themeLegend"),
              light: tNav("themeLight"),
              system: tNav("themeSystem"),
              dark: tNav("themeDark"),
            }}
          />
        ),
        elements: [
          {
            key: "terms",
            label: tNav("termsOfUse"),
            onClick: () => {
              window.open(
                "https://camunda.com/legal/terms/camunda-platform/camunda-platform-8-saas-trial/",
                "_blank",
              );
            },
          },
          {
            key: "privacy",
            label: tNav("privacyPolicy"),
            onClick: () => {
              window.open("https://camunda.com/legal/privacy/", "_blank");
            },
          },
          {
            key: "imprint",
            label: tNav("imprint"),
            onClick: () => {
              window.open("https://camunda.com/legal/imprint/", "_blank");
            },
          },
        ],
      },
    });

    const sidebarChildren: SidebarNodeDescriptor[] = hideNavLinks
      ? []
      : routes.map((route) => ({
          type: "item",
          key: route.key,
          label: route.label,
          icon: ROUTE_ICONS[route.key] ?? Document,
          isActive: route.isCurrentPage,
          linkProps: { to: route.key } as never,
        }));

    const licenseTag = getLicenseTag(license);

    const { navProps } = useC3NavigationV2({
      app: {
        ariaLabel: "Admin",
        linkProps: { to: "/" } as never,
      },
      skipToContentTargetId: SKIP_TO_CONTENT_TARGET_ID,
      sidebarLabels: {
        collapse: tNav("sidebarCollapse"),
        expand: tNav("sidebarExpand"),
        toggleAriaLabel: (expanded) =>
          expanded ? tNav("sidebarCollapseAria") : tNav("sidebarExpandAria"),
        groupToggleAriaLabel: ({ label, isExpanded }) =>
          isExpanded
            ? tNav("sidebarGroupCollapseAria", { label })
            : tNav("sidebarGroupExpandAria", { label }),
      },
      activeItemKey: activeKey,
      sidebarChildren,
      breadcrumbs,
      tools,
      linkComponent: Link as never,
      headerTrailingContent: licenseTag.show ? (
        <C3LicenseTag
          isProductionLicense={licenseTag.isProductionLicense}
          isCommercial={licenseTag.isCommercial}
          expiresAt={licenseTag.expiresAt}
        />
      ) : undefined,
    });

    return (
      <ToolsProvider>
        <C3NavigationV2 {...navProps} />
      </ToolsProvider>
    );
  },
);

function getLicenseTag(license: LicenseDto | null | undefined) {
  if (license === undefined || license === null) {
    return {
      show: true,
      isProductionLicense: false,
      isCommercial: false,
      expiresAt: undefined as number | string | undefined,
    };
  }

  return {
    show: license.licenseType === undefined || license.licenseType != "saas",
    isProductionLicense: license.validLicense,
    isCommercial: license.isCommercial,
    expiresAt: license.expiresAt ?? undefined,
  };
}

export default AppHeaderV2;
