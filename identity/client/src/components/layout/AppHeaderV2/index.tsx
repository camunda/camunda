/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  C3LicenseTag,
  preview_C3ToolsArea as C3ToolsArea,
  preview_useCamundaTools as useCamundaTools,
  type UseCamundaToolsOptions,
} from "@camunda/camunda-composite-components";
import {
  AppHeader,
  AppSidebar,
  CamundaLogo,
  Text,
  type GlobalActionButton,
  type UserMenuItem,
} from "@camunda/design-system";
import type { License as LicenseDto } from "@camunda/camunda-api-zod-schemas/8.10";
import { useCallback, useMemo, type MouseEvent } from "react";
import { useLocation } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";

import { logout } from "src/utility/auth";
import { useNotifications } from "src/components/notifications";
import useTranslate from "src/utility/localization";
import { isSaaS } from "src/configuration";
import { licenseQueries } from "src/utility/api/headers/queries.ts";
import { authenticationQueries } from "src/utility/api/authentication/queries.ts";

import { ForwardRefLink } from "./ForwardRefLink";
import { InfoMenu } from "./InfoMenu";
import { LogoutAwareUserMenu } from "./LogoutAwareUserMenu";
import { ThemeSelector } from "./ThemeSelector";
import { useBreadcrumbs } from "./useBreadcrumbs";
import { useSidebarChildren } from "./useSidebarChildren";

const SKIP_TO_CONTENT_TARGET_ID = "main-content";
const LOGOUT_DELAY = 1000;

const USER_MENU_LINKS = [
  {
    key: "terms",
    labelKey: "termsOfUse",
    href: "https://camunda.com/legal/terms/camunda-platform/camunda-platform-8-saas-trial/",
  },
  {
    key: "privacy",
    labelKey: "privacyPolicy",
    href: "https://camunda.com/legal/privacy/",
  },
  {
    key: "imprint",
    labelKey: "imprint",
    href: "https://camunda.com/legal/imprint/",
  },
] as const;

/**
 * Build and copyright line at the foot of the user menu — the version the C3
 * user panel used to render from `options.user.version`.
 */
const VersionFooter = () => {
  const { t } = useTranslate("navigation");

  return (
    <div className="px-2 py-1.5">
      <Text variant="helper" as="p">
        {t("version", { version: import.meta.env.VITE_APP_VERSION })}
      </Text>
      <Text variant="helper" as="p">
        {t("copyright", { year: new Date().getFullYear() })}
      </Text>
    </div>
  );
};

const AppHeaderV2 = ({ hideNavLinks = false }: { hideNavLinks?: boolean }) => {
  const { data: license } = useQuery(licenseQueries.current());
  const { data: camundaUser } = useQuery(authenticationQueries.me());
  const { enqueueNotification } = useNotifications();
  const { t } = useTranslate("authentication");
  const { t: tNav } = useTranslate("navigation");
  const { pathname, search } = useLocation();

  const logoutWithNotification = useCallback(() => {
    enqueueNotification({
      kind: "info",
      title: t("logOut"),
      subtitle: t("beingLoggedOut"),
    });
    setTimeout(logout, LOGOUT_DELAY);
  }, [enqueueNotification, t]);

  /**
   * Intercepts and fixes "skip to content" navigation. It's bare `href="#main-content"`
   * does not work with the `<base href>` for Thymeleaf's contextPath support configured
   * in the app's `index.html`. Without the intercept, "skip to content" resolved against
   * the "base" path instead of the current URL, which breaks the app.
   */
  const handleSkipToContentClick = useCallback(
    (event: MouseEvent<HTMLElement>) => {
      const { target } = event;
      if (
        !(target instanceof HTMLAnchorElement) ||
        target.getAttribute("href") !== `#${SKIP_TO_CONTENT_TARGET_ID}`
      ) {
        return;
      }

      event.preventDefault();
      window.location.hash = SKIP_TO_CONTENT_TARGET_ID;
      document.getElementById(SKIP_TO_CONTENT_TARGET_ID)?.focus();
    },
    [],
  );

  const toolsOptions = useMemo<UseCamundaToolsOptions>(
    () => ({
      notifications: isSaaS
        ? {
            title: tNav("notifications"),
            ariaLabel: tNav("notifications"),
            labels: {
              dismissAll: tNav("notificationsDismissAll"),
              emptyTitle: tNav("notificationsEmptyTitle"),
              emptyDescription: tNav("notificationsEmptyDescription"),
            },
          }
        : undefined,
    }),
    [tNav],
  );
  const { tools, ToolsProvider } = useCamundaTools(toolsOptions);
  const globalActions = useMemo<GlobalActionButton[]>(() => {
    return isSaaS
      ? [
          {
            key: "notifications",
            label: tNav("notifications"),
            element: <C3ToolsArea tools={tools} />,
          },
          {
            key: "info",
            label: tNav("info"),
            element: <InfoMenu />,
          },
        ]
      : [
          {
            key: "info",
            label: tNav("info"),
            element: <InfoMenu />,
          },
        ];
  }, [tNav, tools]);

  const breadcrumb = useBreadcrumbs();
  const sidebarChildren = useSidebarChildren(hideNavLinks);
  const hasSidebar = sidebarChildren.length > 0;
  const userMenuItems = useMemo<UserMenuItem[]>(
    () =>
      USER_MENU_LINKS.map(({ key, labelKey, href }) => ({
        key,
        label: tNav(labelKey),
        onClick: () => window.open(href, "_blank", "noopener,noreferrer"),
      })),
    [tNav],
  );

  const licenseTag = getLicenseTag(license);

  return (
    <ToolsProvider>
      <AppHeader
        onClick={handleSkipToContentClick}
        skipToContentTargetId={SKIP_TO_CONTENT_TARGET_ID}
        showSidebarTrigger={hasSidebar}
        logo={
          <ForwardRefLink
            to="/"
            aria-label="Camunda Admin"
            className="flex items-center"
          >
            <CamundaLogo />
          </ForwardRefLink>
        }
        breadcrumb={breadcrumb}
        trailing={
          licenseTag.show ? (
            <div className="flex items-center">
              <C3LicenseTag
                isProductionLicense={licenseTag.isProductionLicense}
                isCommercial={licenseTag.isCommercial}
                expiresAt={licenseTag.expiresAt}
              />
            </div>
          ) : undefined
        }
        globalActions={globalActions}
        actions={
          camundaUser === undefined ? undefined : (
            <LogoutAwareUserMenu
              userName={camundaUser.displayName}
              userEmail={camundaUser.email}
              canLogout={Boolean(camundaUser.canLogout)}
              onLogout={logoutWithNotification}
              items={userMenuItems}
              customSection={
                <>
                  <ThemeSelector />
                  <VersionFooter />
                </>
              }
            />
          )
        }
      />
      {hasSidebar && (
        <AppSidebar
          ariaLabel={tNav("mainNavigation")}
          items={sidebarChildren}
          activeItemKey={pathname + search}
          linkComponent={ForwardRefLink}
        />
      )}
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
