/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  C3ThemeSelector,
  preview_useCamundaTools as useCamundaTools,
  type UseCamundaToolsOptions,
} from "@camunda/camunda-composite-components";

import useTranslate from "src/utility/localization";
import { isSaaS } from "src/configuration";
import { themeStore, isThemeOption } from "src/common/theme/theme";

type ToolsConfigParams = {
  name: string;
  email: string;
  canLogout: boolean;
  onLogout: () => void;
};

export function useCamundaToolsConfig({
  name,
  email,
  canLogout,
  onLogout,
}: ToolsConfigParams) {
  const { t } = useTranslate("authentication");
  const { t: tNav } = useTranslate("navigation");

  const options = {
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
          onClick: () => window.open("https://academy.camunda.com/", "_blank"),
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
      name,
      email,
      onLogout: canLogout ? onLogout : undefined,
      labels: { logOut: t("logOut") },
      customSection: (
        <C3ThemeSelector
          currentTheme={themeStore.selectedTheme}
          onChange={(theme) => {
            if (isThemeOption(theme)) {
              themeStore.changeTheme(theme);
            }
          }}
          labels={{
            legend: tNav("themeSelectorLegend"),
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
          onClick: () =>
            window.open(
              "https://camunda.com/legal/terms/camunda-platform/camunda-platform-8-saas-trial/",
              "_blank",
            ),
        },
        {
          key: "privacy",
          label: tNav("privacyPolicy"),
          onClick: () =>
            window.open("https://camunda.com/legal/privacy/", "_blank"),
        },
        {
          key: "imprint",
          label: tNav("imprint"),
          onClick: () =>
            window.open("https://camunda.com/legal/imprint/", "_blank"),
        },
      ],
    },
  } satisfies UseCamundaToolsOptions;

  return useCamundaTools(options);
}
