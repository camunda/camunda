/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  C3LicenseTag,
  preview_C3NavigationV2 as C3NavigationV2,
  preview_useC3NavigationV2 as useC3NavigationV2,
  preview_useClusterWebappBreadcrumbs as useClusterWebappBreadcrumbs,
} from "@camunda/camunda-composite-components";
import type { License as LicenseDto } from "@camunda/camunda-api-zod-schemas/8.10";
import { useCallback } from "react";
import { Link } from "react-router-dom";

import { logout } from "src/utility/auth";
import { useNotifications } from "src/components/notifications";
import useTranslate from "src/utility/localization";

import { useSidebarChildren } from "./useSidebarChildren";
import { useCamundaToolsConfig } from "./useCamundaToolsConfig";
import { licenseQueries } from "src/utility/api/headers/queries.ts";
import { useQuery } from "@tanstack/react-query";
import { authenticationQueries } from "src/utility/api/authentication/queries.ts";

const SKIP_TO_CONTENT_TARGET_ID = "main-content";
const LOGOUT_DELAY = 1000;

const AppHeaderV2 = ({ hideNavLinks = false }: { hideNavLinks?: boolean }) => {
  const { data: license } = useQuery(licenseQueries.current());
  const { data: camundaUser } = useQuery(authenticationQueries.me());
  const { enqueueNotification } = useNotifications();
  const { t } = useTranslate("authentication");
  const { t: tNav } = useTranslate("navigation");

  const logoutWithNotification = useCallback(async () => {
    enqueueNotification({
      kind: "info",
      title: t("logOut"),
      subtitle: t("beingLoggedOut"),
    });
    return setTimeout(logout, LOGOUT_DELAY);
  }, [enqueueNotification, t]);

  const breadcrumbs = useClusterWebappBreadcrumbs({ currentApp: "identity" });
  const sidebarChildren = useSidebarChildren(hideNavLinks);
  const { tools, ToolsProvider } = useCamundaToolsConfig({
    name: camundaUser?.displayName ?? "",
    email: camundaUser?.email ?? "",
    canLogout: Boolean(camundaUser?.canLogout),
    onLogout: logoutWithNotification,
  });

  const licenseTag = getLicenseTag(license);

  const { navProps } = useC3NavigationV2({
    app: {
      ariaLabel: "Admin",
      linkProps: { to: "/" },
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
    activeItemKey: "",
    sidebarChildren,
    breadcrumbs,
    tools,
    // @ts-expect-error - we need to fix it from the C3 side
    linkComponent: Link,
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
};

function getLicenseTag(license: LicenseDto | null | undefined) {
  if (license === undefined || license === null) {
    return {
      show: true,
      isProductionLicense: false,
      isCommercial: false,
      expiresAt: undefined,
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
