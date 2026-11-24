/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useState} from 'react';
import {observer} from 'mobx-react-lite';
import {Link} from 'react-router';
import {ArrowRight} from '@carbon/react/icons';
import {C3Navigation} from '@camunda/camunda-composite-components';
import {Locations, Paths} from 'modules/Routes';
import {authenticationStore} from 'modules/stores/authentication';
import {useCurrentPage} from 'modules/hooks/useCurrentPage';
import {licenseTagStore} from 'modules/stores/licenseTag';
import {currentTheme} from 'modules/stores/currentTheme';
import {useCurrentUser} from 'modules/queries/useCurrentUser';
import {isForbidden} from 'modules/auth/isForbidden';
import {getClientConfig} from 'modules/utils/getClientConfig';
import {notificationsStore} from 'modules/stores/notifications';

function getInfoSidebarItems(isPaidPlan: boolean) {
  const BASE_INFO_SIDEBAR_ITEMS = [
    {
      key: 'docs',
      label: 'Documentation',
      onClick: () => {
        window.open('https://docs.camunda.io/', '_blank');
      },
    },
    {
      key: 'academy',
      label: 'Camunda Academy',
      onClick: () => {
        window.open('https://academy.camunda.com/', '_blank');
      },
    },
  ];
  const FEEDBACK_AND_SUPPORT_ITEM = {
    key: 'feedbackAndSupport',
    label: 'Feedback and Support',
    onClick: () => {
      window.open('https://jira.camunda.com/projects/SUPPORT/queues', '_blank');
    },
  } as const;
  const COMMUNITY_FORUM_ITEM = {
    key: 'communityForum',
    label: 'Community Forum',
    onClick: () => {
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

const NAVBAR_LG_BREAKPOINT = '(min-width: 66rem)';

const LOGOUT_DELAY = 1000;

const AppHeader: React.FC = observer(() => {
  const {data: currentUser} = useCurrentUser();
  const clientConfig = getClientConfig();
  const IS_SAAS = typeof clientConfig.organizationId === 'string';
  const {currentPage} = useCurrentPage();
  const {theme, changeTheme} = currentTheme;
  const [isAppBarOpen, setIsAppBarOpen] = useState(false);
  const [isLargeScreen, setIsLargeScreen] = useState(
    () => window.matchMedia(NAVBAR_LG_BREAKPOINT).matches,
  );

  useEffect(() => {
    const mql = window.matchMedia(NAVBAR_LG_BREAKPOINT);
    const handler = (e: MediaQueryListEvent) => setIsLargeScreen(e.matches);
    mql.addEventListener('change', handler);
    return () => mql.removeEventListener('change', handler);
  }, []);

  useEffect(() => {
    licenseTagStore.fetchLicense();

    return licenseTagStore.reset;
  }, []);

  const logoutWithNotification = async () => {
    notificationsStore.displayNotification({
      kind: 'info',
      title: 'Log Out',
      subtitle: 'You are being logged out...',
      isDismissable: true,
    });
    return setTimeout(authenticationStore.handleLogout, LOGOUT_DELAY);
  };

  return (
    <C3Navigation
      toggleAppbar={(isAppBarOpen) => setIsAppBarOpen(isAppBarOpen)}
      notificationSideBar={IS_SAAS ? {} : undefined}
      appBar={{
        ariaLabel: 'App panel',
        isOpen: isAppBarOpen,
        appTeaserRouteProps: IS_SAAS ? {} : undefined,
        elements: IS_SAAS ? undefined : [],
      }}
      app={{
        ariaLabel: 'Camunda Operate',
        name: 'Operate',
        routeProps: {
          to: Paths.dashboard(),
        },
      }}
      forwardRef={Link}
      navbar={{
        elements: isForbidden(currentUser)
          ? []
          : [
              {
                key: 'dashboard',
                label: 'Dashboard',
                isCurrentPage: currentPage === 'dashboard',
                routeProps: {
                  to: Paths.dashboard(),
                },
              },
              {
                key: 'processes',
                label: 'Processes',
                isCurrentPage:
                  currentPage === 'processes' ||
                  currentPage?.startsWith('process-details') === true,
                routeProps: {
                  to: Locations.processes(),
                  state: {refreshContent: true, hideOptionalFilters: true},
                },
              },
              {
                key: 'decisions',
                label: 'Decisions',
                isCurrentPage:
                  currentPage === 'decisions' ||
                  currentPage === 'decision-details',
                routeProps: {
                  to: Locations.decisions(),
                  state: {refreshContent: true, hideOptionalFilters: true},
                },
              },
              ...(isLargeScreen
                ? [
                    {
                      key: 'operations',
                      label: 'Operations',
                      isCurrentPage:
                        currentPage === 'batch-operations' ||
                        currentPage === 'operations-log',
                      subElements: [
                        {
                          key: 'batch-operations',
                          label: 'Batch operations',
                          isCurrentPage: currentPage === 'batch-operations',
                          routeProps: {
                            to: Paths.batchOperations(),
                            onClick: () => {
                              (document.activeElement as HTMLElement)?.blur();
                            },
                          },
                        },
                        {
                          key: 'operations-log',
                          label: 'Operations log',
                          isCurrentPage: currentPage === 'operations-log',
                          routeProps: {
                            to: Paths.operationsLog(),
                            onClick: () => {
                              (document.activeElement as HTMLElement)?.blur();
                            },
                          },
                        },
                      ],
                    },
                  ]
                : [
                    {
                      key: 'batch-operations',
                      label: 'Batch operations',
                      isCurrentPage: currentPage === 'batch-operations',
                      routeProps: {
                        to: Paths.batchOperations(),
                      },
                    },
                    {
                      key: 'operations-log',
                      label: 'Operations log',
                      isCurrentPage: currentPage === 'operations-log',
                      routeProps: {
                        to: Paths.operationsLog(),
                      },
                    },
                  ]),
            ],
        licenseTag: {
          show: licenseTagStore.state.isTagVisible,
          isProductionLicense: licenseTagStore.state.isProductionLicense,
          isCommercial: licenseTagStore.state.isCommercial,
          expiresAt: licenseTagStore.state.expiresAt,
        },
      }}
      infoSideBar={{
        isOpen: false,
        ariaLabel: 'Info',
        elements: getInfoSidebarItems(
          typeof currentUser?.salesPlanType === 'string' &&
            ['paid-cc', 'enterprise'].includes(currentUser.salesPlanType),
        ),
      }}
      userSideBar={{
        ariaLabel: 'Settings',
        version: import.meta.env.VITE_VERSION,
        customElements: {
          profile: {
            label: 'Profile',
            user: {
              name: currentUser?.displayName ?? '',
              email: currentUser?.email ?? '',
            },
          },
          themeSelector: {
            currentTheme: theme,
            onChange: (theme: string) => {
              changeTheme(theme as 'system' | 'dark' | 'light');
            },
          },
        },
        elements: [
          ...(window.Osano?.cm === undefined
            ? []
            : [
                {
                  key: 'cookie',
                  label: 'Cookie preferences',
                  onClick: () => {
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
              window.open('https://camunda.com/legal/privacy/', '_blank');
            },
          },
          {
            key: 'imprint',
            label: 'Imprint',
            onClick: () => {
              window.open('https://camunda.com/legal/imprint/', '_blank');
            },
          },
        ],
        bottomElements: clientConfig.canLogout
          ? [
              {
                key: 'logout',
                label: 'Log out',
                renderIcon: ArrowRight,
                kind: 'ghost',
                onClick: logoutWithNotification,
              },
            ]
          : undefined,
      }}
    />
  );
});

export {AppHeader};
