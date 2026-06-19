/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  preview_useCamundaTools as useCamundaTools,
  type UseCamundaToolsOptions,
} from '@camunda/camunda-composite-components';
import type {CurrentUser} from '@camunda/camunda-api-zod-schemas/8.10';
import {tracking} from 'modules/tracking';
import {getClientConfig} from 'modules/config/getClientConfig';
import {authenticationStore} from 'modules/auth/authentication';
import {notificationsStore} from 'modules/notifications/notifications.store';
import {t} from 'i18next';
import {useMemo} from 'react';
import {ThemeSelector} from './ThemeSelector';
import {LanguageSelector} from './LanguageSelector';

function getInfoSidebarItems(isPaidPlan: boolean) {
  const BASE_INFO_SIDEBAR_ITEMS = [
    {
      key: 'docs',
      label: t('headerSidebarDocumentationLink'),
      onClick: () => {
        tracking.track({eventName: 'info-bar', link: 'documentation'});
        window.open('https://docs.camunda.io/', '_blank');
      },
    },
    {
      key: 'academy',
      label: t('headerSidebarCamundaAcademyLink'),
      onClick: () => {
        tracking.track({eventName: 'info-bar', link: 'academy'});
        window.open('https://academy.camunda.com/', '_blank');
      },
    },
  ];
  const FEEDBACK_AND_SUPPORT_ITEM = {
    key: 'feedbackAndSupport',
    label: t('headerSidebarFeedbackAndSupportLink'),
    onClick: () => {
      tracking.track({eventName: 'info-bar', link: 'feedback'});
      window.open('https://jira.camunda.com/projects/SUPPORT/queues', '_blank');
    },
  } as const;
  const COMMUNITY_FORUM_ITEM = {
    key: 'communityForum',
    label: t('headerSidebarCommunityForumLink'),
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

const LOGOUT_DELAY = 1000;

const logoutWithNotification = async () => {
  notificationsStore.displayNotification({
    kind: 'info',
    title: t('notificationLogOutTitle'),
    subtitle: t('notificationLogOutSubtitle'),
    isDismissable: true,
  });
  setTimeout(authenticationStore.handleLogout, LOGOUT_DELAY);
};

function useCamundaToolsConfig(params: {currentUser: CurrentUser | undefined}) {
  const {currentUser} = params;

  const options = useMemo(() => {
    const isPaidPlan = ['paid-cc', 'enterprise'].includes(
      currentUser?.salesPlanType ?? '',
    );
    const isSaas = getClientConfig().organizationId !== null;

    return {
      notifications: isSaas
        ? {
            title: t('headerNotificationsLabel'),
            labels: {
              dismissAll: t('notificationsDismissAll'),
              emptyTitle: t('notificationsEmptyTitle'),
              emptyDescription: t('notificationsEmptyDescription'),
            },
          }
        : undefined,
      info: {
        ariaLabel: t('headerInfoLabel'),
        title: t('headerInfoLabel'),
        elements: getInfoSidebarItems(isPaidPlan),
      },
      user: {
        ariaLabel: t('headerSettingsLabel'),
        title: t('headerSettingsLabel'),
        version: import.meta.env.VITE_VERSION,
        name: currentUser?.displayName ?? '',
        email: currentUser?.email ?? '',
        onLogout: getClientConfig().canLogout
          ? logoutWithNotification
          : undefined,
        labels: {logOut: t('headerLogOutLabel')},
        customSection: (
          <div>
            <ThemeSelector />
            <LanguageSelector />
          </div>
        ),
        elements: [
          ...(window.Osano?.cm === undefined
            ? []
            : [
                {
                  key: 'cookie',
                  label: t('headerCookiePreferencesLabel'),
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
            label: t('headerTermsOfUseLabel'),
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
            label: t('headerPrivacyPolicyLabel'),
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
            label: t('headerImprintLabel'),
            onClick: () => {
              tracking.track({eventName: 'user-side-bar', link: 'imprint'});
              window.open('https://camunda.com/legal/imprint/', '_blank');
            },
          },
        ],
      },
    };
  }, [currentUser]) satisfies UseCamundaToolsOptions;

  return useCamundaTools(options);
}

export {useCamundaToolsConfig};
