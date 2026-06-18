/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {preview_useCamundaTools as useCamundaTools} from '@camunda/camunda-composite-components';
import type {CurrentUser} from '@camunda/camunda-api-zod-schemas/8.10';
import {tracking} from 'modules/tracking';
import {getClientConfig} from 'modules/config/getClientConfig';
import {authenticationStore} from 'modules/auth/authentication';
import {notificationsStore} from 'modules/notifications/notifications.store';
import {getInfoSidebarItems} from './info-sidebar-items';

const LOGOUT_DELAY = 1000;

export function useCamundaToolsConfig(params: {
  currentUser: CurrentUser | undefined;
  isPaidPlan: boolean;
  isSaas: boolean;
  customSection: React.ReactNode;
}) {
  const {t} = useTranslation();
  const {currentUser, isPaidPlan, isSaas, customSection} = params;

  const logoutWithNotification = async () => {
    notificationsStore.displayNotification({
      kind: 'info',
      title: t('notificationLogOutTitle'),
      subtitle: t('notificationLogOutSubtitle'),
      isDismissable: true,
    });
    return setTimeout(authenticationStore.handleLogout, LOGOUT_DELAY);
  };

  return useCamundaTools({
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
      customSection,
      elements: [
        ...(window.Osano?.cm === undefined
          ? []
          : [
              {
                key: 'cookie',
                label: t('headerCookiePreferencesLabel'),
                onClick: () => {
                  tracking.track({eventName: 'user-side-bar', link: 'cookies'});
                  window.Osano?.cm?.showDrawer('osano-cm-dom-info-dialog-open');
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
  });
}
