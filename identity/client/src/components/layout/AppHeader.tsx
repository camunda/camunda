/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { C3Navigation } from "@camunda/camunda-composite-components";
import { useGlobalRoutes } from "src/components/global/useGlobalRoutes";
import { Link } from "react-router-dom";
import { useApi } from "src/utility/api";
import { checkLicense, License } from "src/utility/api/headers";
import { getAuthentication } from "src/utility/api/authentication";
import { ArrowRight } from "@carbon/react/icons";
import { logout } from "src/utility/auth";
import { useState } from "react";
import { isSaaS } from "src/configuration";
import { useNotifications } from "src/components/notifications";
import useTranslate from "src/utility/localization";

const LOGOUT_DELAY = 1000;

const AppHeader = ({ hideNavLinks = false }) => {
  const routes = useGlobalRoutes();
  const { data: license } = useApi(checkLicense);
  const { data: camundaUser } = useApi(getAuthentication);
  const [isAppBarOpen, setIsAppBarOpen] = useState(false);
  const { enqueueNotification } = useNotifications();
  const { t } = useTranslate("authentication");

  const logoutWithNotification = async () => {
    enqueueNotification({
      kind: "info",
      title: t("logOut"),
      subtitle: t("beingLoggedOut"),
    });
    return setTimeout(logout, LOGOUT_DELAY);
  };

  return (
    <C3Navigation
      toggleAppbar={(isAppBarOpen) => setIsAppBarOpen(isAppBarOpen)}
      app={{
        name: "Identity",
        ariaLabel: "Identity",
        routeProps: {
          to: "/",
        },
      }}
      forwardRef={Link}
      appBar={{
        ariaLabel: "App panel",
        isOpen: isAppBarOpen,
        elements: isSaaS ? undefined : [],
        appTeaserRouteProps: isSaaS ? {} : undefined,
      }}
      navbar={{
        elements: hideNavLinks
          ? []
          : routes.map((route) => ({
              ...route,
              routeProps: {
                to: route.key,
              },
            })),
        licenseTag: getLicenseTag(license),
      }}
      userSideBar={{
        version: import.meta.env.VITE_APP_VERSION,
        ariaLabel: "Settings",
        customElements: {
          profile: {
            label: "Profile",
            user: {
              name: camundaUser?.displayName ?? "",
              email: camundaUser?.email ?? "",
            },
          },
        },
        elements: [
          {
            key: "terms",
            label: "Terms of use",
            onClick: () => {
              window.open(
                "https://camunda.com/legal/terms/camunda-platform/camunda-platform-8-saas-trial/",
                "_blank",
              );
            },
          },
          {
            key: "privacy",
            label: "Privacy policy",
            onClick: () => {
              window.open("https://camunda.com/legal/privacy/", "_blank");
            },
          },
          {
            key: "imprint",
            label: "Imprint",
            onClick: () => {
              window.open("https://camunda.com/legal/imprint/", "_blank");
            },
          },
        ],
        bottomElements: camundaUser?.canLogout
          ? [
              {
                key: "logout",
                label: "Log out",
                renderIcon: ArrowRight,
                kind: "ghost",
                onClick: logoutWithNotification,
              },
            ]
          : undefined,
      }}
    />
  );
};

function getLicenseTag(license: License | null) {
  if (license === null) {
    return {
      show: true,
      isProductionLicense: false,
      isCommercial: false,
    };
  }

  return {
    show: license.licenseType === undefined || license.licenseType != "saas",
    isProductionLicense: license.validLicense,
    isCommercial: license.isCommercial,
    expiresAt: license.expiresAt ?? undefined,
  };
}

export default AppHeader;
