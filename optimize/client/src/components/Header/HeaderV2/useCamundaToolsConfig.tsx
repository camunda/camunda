/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo} from 'react';
import {
  preview_useCamundaTools as useCamundaTools,
  type UseCamundaToolsOptions,
} from '@camunda/camunda-composite-components';

import {t} from 'translation';

type ToolsConfigParams = {
  noActions?: boolean;
  isCloud: boolean;
  enterpriseMode: boolean;
  optimizeVersion: string;
  userName: string;
  userEmail: string;
  docsUrl: string;
  timezone: string;
  logoutHidden: boolean;
  onLogout: () => void;
};

export default function useCamundaToolsConfig(params: ToolsConfigParams) {
  const {
    noActions,
    isCloud,
    enterpriseMode,
    optimizeVersion,
    userName,
    userEmail,
    docsUrl,
    timezone,
    logoutHidden,
    onLogout,
  } = params;

  const options = useMemo(
    () =>
      ({
        notifications: isCloud
          ? {
              title: t('navigation.notifications').toString(),
              labels: {
                dismissAll: t('navigation.notificationsDismissAll').toString(),
                emptyTitle: t('navigation.notificationsEmptyTitle').toString(),
                emptyDescription: t('navigation.notificationsEmptyDescription').toString(),
              },
            }
          : undefined,
        info: noActions
          ? undefined
          : {
              ariaLabel: t('navigation.info').toString(),
              title: t('navigation.info').toString(),
              elements: [
                {
                  key: 'documentation',
                  label: t('navigation.documentation').toString(),
                  onClick: () => window.open(docsUrl, '_blank'),
                },
                {
                  key: 'academy',
                  label: t('navigation.academy').toString(),
                  onClick: () => window.open('https://academy.camunda.com/', '_blank'),
                },
                {
                  key: 'feedbackAndSupport',
                  label: t('navigation.feedback').toString(),
                  onClick: () =>
                    window.open(
                      enterpriseMode
                        ? 'https://jira.camunda.com/projects/SUPPORT/queues'
                        : 'https://forum.camunda.io/',
                      '_blank'
                    ),
                },
                {
                  key: 'slackCommunityChannel',
                  label: 'Slack Community Channel',
                  onClick: () => window.open('https://camunda.com/slack', '_blank'),
                },
              ],
            },
        user: noActions
          ? undefined
          : {
              ariaLabel: t('navigation.settings').toString(),
              title: t('navigation.settings').toString(),
              version: optimizeVersion,
              name: userName,
              email: userEmail,
              onLogout: logoutHidden ? undefined : onLogout,
              labels: {logOut: t('navigation.logout').toString()},
              customSection: <div className="HeaderV2-timezone">{timezone}</div>,
              elements: [
                {
                  key: 'terms',
                  label: t('navigation.termsOfUse').toString(),
                  onClick: () =>
                    window.open(
                      'https://camunda.com/legal/terms/camunda-platform/camunda-platform-8-saas-trial/',
                      '_blank'
                    ),
                },
                {
                  key: 'privacy',
                  label: t('navigation.privacyPolicy').toString(),
                  onClick: () => window.open('https://camunda.com/legal/privacy/', '_blank'),
                },
                {
                  key: 'imprint',
                  label: t('navigation.imprint').toString(),
                  onClick: () => window.open('https://camunda.com/legal/imprint/', '_blank'),
                },
              ],
            },
      }) satisfies UseCamundaToolsOptions,
    [
      noActions,
      isCloud,
      enterpriseMode,
      optimizeVersion,
      userName,
      userEmail,
      docsUrl,
      timezone,
      logoutHidden,
      onLogout,
    ]
  );

  return useCamundaTools(options);
}
