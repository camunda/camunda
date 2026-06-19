/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo} from 'react';
import {
  C3ThemeSelector,
  preview_useCamundaTools as useCamundaTools,
  type UseCamundaToolsOptions,
} from '@camunda/camunda-composite-components';
import {tracking} from 'modules/tracking';

function getInfoSidebarItems(isPaidPlan: boolean) {
  const BASE_INFO_SIDEBAR_ITEMS = [
    {
      key: 'docs',
      label: 'Documentation',
      onClick: () => {
        tracking.track({eventName: 'info-bar', link: 'documentation'});
        window.open('https://docs.camunda.io/', '_blank');
      },
    },
    {
      key: 'academy',
      label: 'Camunda Academy',
      onClick: () => {
        tracking.track({eventName: 'info-bar', link: 'academy'});
        window.open('https://academy.camunda.com/', '_blank');
      },
    },
  ];
  const FEEDBACK_AND_SUPPORT_ITEM = {
    key: 'feedbackAndSupport',
    label: 'Feedback and Support',
    onClick: () => {
      tracking.track({eventName: 'info-bar', link: 'feedback'});
      window.open('https://jira.camunda.com/projects/SUPPORT/queues', '_blank');
    },
  };
  const COMMUNITY_FORUM_ITEM = {
    key: 'communityForum',
    label: 'Community Forum',
    onClick: () => {
      tracking.track({eventName: 'info-bar', link: 'forum'});
      window.open('https://forum.camunda.io', '_blank');
    },
  };

  return isPaidPlan
    ? [
        ...BASE_INFO_SIDEBAR_ITEMS,
        FEEDBACK_AND_SUPPORT_ITEM,
        COMMUNITY_FORUM_ITEM,
      ]
    : [...BASE_INFO_SIDEBAR_ITEMS, COMMUNITY_FORUM_ITEM];
}

type ThemeOption = 'light' | 'dark' | 'system';

type ToolsConfigParams = {
  isSaas: boolean;
  isPaidPlan: boolean;
  userName: string;
  userEmail: string;
  canLogout: boolean;
  onLogout: () => void;
  selectedTheme: ThemeOption;
  onThemeChange: (theme: ThemeOption) => void;
};

export function useCamundaToolsConfig(params: ToolsConfigParams) {
  const {
    isSaas,
    isPaidPlan,
    userName,
    userEmail,
    canLogout,
    onLogout,
    selectedTheme,
    onThemeChange,
  } = params;

  const options = useMemo(
    () =>
      ({
        notifications: isSaas ? {} : undefined,
        info: {
          ariaLabel: 'Info',
          title: 'Info',
          elements: getInfoSidebarItems(isPaidPlan),
        },
        user: {
          ariaLabel: 'Settings',
          title: 'Settings',
          version: import.meta.env.VITE_VERSION,
          name: userName,
          email: userEmail,
          onLogout: canLogout ? onLogout : undefined,
          customSection: (
            <C3ThemeSelector
              currentTheme={selectedTheme}
              onChange={onThemeChange}
            />
          ),
          elements: [
            ...(window.Osano?.cm === undefined
              ? []
              : [
                  {
                    key: 'cookie',
                    label: 'Cookie preferences',
                    onClick: () => {
                      tracking.track({
                        eventName: 'user-side-bar',
                        link: 'cookies',
                      });
                      window.Osano?.cm?.showDrawer(
                        'osano-cm-dom-info-dialog-open',
                      );
                    },
                  },
                ]),
            {
              key: 'terms',
              label: 'Terms of use',
              onClick: () => {
                tracking.track({
                  eventName: 'user-side-bar',
                  link: 'terms-conditions',
                });
                window.open(
                  'https://camunda.com/legal/terms/camunda-platform/camunda-platform-8-saas-trial/',
                  '_blank',
                );
              },
            },
            {
              key: 'privacy',
              label: 'Privacy policy',
              onClick: () => {
                tracking.track({
                  eventName: 'user-side-bar',
                  link: 'privacy-policy',
                });
                window.open('https://camunda.com/legal/privacy/', '_blank');
              },
            },
            {
              key: 'imprint',
              label: 'Imprint',
              onClick: () => {
                tracking.track({eventName: 'user-side-bar', link: 'imprint'});
                window.open('https://camunda.com/legal/imprint/', '_blank');
              },
            },
          ],
        },
      }) satisfies UseCamundaToolsOptions,
    [
      isSaas,
      isPaidPlan,
      userName,
      userEmail,
      canLogout,
      onLogout,
      selectedTheme,
      onThemeChange,
    ],
  );

  return useCamundaTools(options);
}
