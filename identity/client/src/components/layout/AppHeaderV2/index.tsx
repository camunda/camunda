/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  AppHeader,
  AppSidebar,
  CamundaLogo,
  Text,
  useIsMobile,
  type GlobalActionButton,
  type UserMenuItem,
} from "@camunda/design-system";
import { SaasNotifications } from "@camunda/oc-saas-notifications";
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
import { LicenseBadges } from "./LicenseBadges";
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
  const { t: tNav, i18n } = useTranslate("navigation");
  const { pathname, search } = useLocation();
  const isMobile = useIsMobile();

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

  const globalActions = useMemo<GlobalActionButton[]>(() => {
    let actions = !isSaaS
      ? []
      : [
          {
            key: "notifications",
            label: tNav("notifications"),
            element: (
              <SaasNotifications
                locale={i18n.resolvedLanguage}
                labels={{
                  title: tNav("notifications"),
                  empty: tNav("notificationsEmpty"),
                  loading: tNav("notificationsLoading"),
                }}
              />
            ),
          },
        ];

    actions.push({
      key: "info",
      label: tNav("info"),
      element: <InfoMenu />,
    });

    return actions;
  }, [tNav, i18n.resolvedLanguage]);

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

  return (
    <>
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
        trailing={isMobile ? undefined : <LicenseBadges license={license} />}
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
    </>
  );
};

export default AppHeaderV2;
