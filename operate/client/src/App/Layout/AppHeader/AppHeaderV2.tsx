/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import {observer} from 'mobx-react-lite';
import {Link} from 'react-router-dom';
import {
  Activity,
  Dashboard,
  DataTable,
  DecisionTree,
  Flow,
  ListChecked,
} from '@carbon/react/icons';
import {
  C3LicenseTag,
  C3ThemeSelector,
  preview_C3NavigationV2 as C3NavigationV2,
  preview_useC3NavigationV2 as useC3NavigationV2,
  preview_useCamundaTools as useCamundaTools,
  preview_useClusterWebappBreadcrumbs as useClusterWebappBreadcrumbs,
  type SidebarNodeDescriptor,
} from '@camunda/camunda-composite-components';
import {Locations, Paths} from 'modules/Routes';
import {tracking} from 'modules/tracking';
import {authenticationStore} from 'modules/stores/authentication';
import {useCurrentPage} from 'modules/hooks/useCurrentPage';
import {licenseTagStore} from 'modules/stores/licenseTag';
import {currentTheme} from 'modules/stores/currentTheme';
import {useCurrentUser} from 'modules/queries/useCurrentUser';
import {isForbidden} from 'modules/auth/isForbidden';
import {getClientConfig} from 'modules/utils/getClientConfig';
import {notificationsStore} from 'modules/stores/notifications';

const SKIP_TO_CONTENT_TARGET_ID = 'main-content';
const LOGOUT_DELAY = 1000;

function getInfoSidebarItems(isPaidPlan: boolean) {
  const BASE_INFO_SIDEBAR_ITEMS = [
    {
      key: 'docs',
      label: 'Documentation',
      onClick: () => {
        tracking.track({
          eventName: 'info-bar',
          link: 'documentation',
        });

        window.open('https://docs.camunda.io/', '_blank');
      },
    },
    {
      key: 'academy',
      label: 'Camunda Academy',
      onClick: () => {
        tracking.track({
          eventName: 'info-bar',
          link: 'academy',
        });

        window.open('https://academy.camunda.com/', '_blank');
      },
    },
  ];
  const FEEDBACK_AND_SUPPORT_ITEM = {
    key: 'feedbackAndSupport',
    label: 'Feedback and Support',
    onClick: () => {
      tracking.track({
        eventName: 'info-bar',
        link: 'feedback',
      });

      window.open('https://jira.camunda.com/projects/SUPPORT/queues', '_blank');
    },
  } as const;
  const COMMUNITY_FORUM_ITEM = {
    key: 'communityForum',
    label: 'Community Forum',
    onClick: () => {
      tracking.track({
        eventName: 'info-bar',
        link: 'forum',
      });

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

const AppHeaderV2: React.FC = observer(() => {
  const {data: currentUser} = useCurrentUser();
  const clientConfig = getClientConfig();
  const IS_SAAS = typeof clientConfig.organizationId === 'string';
  const {currentPage} = useCurrentPage();

  useEffect(() => {
    if (currentUser !== undefined) {
      tracking.identifyUser({
        username: currentUser.username,
        salesPlanType: currentUser.salesPlanType,
        roles: currentUser.roles,
      });
    }
  }, [currentUser]);

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

  const isPaidPlan =
    typeof currentUser?.salesPlanType === 'string' &&
    ['paid-cc', 'enterprise'].includes(currentUser.salesPlanType);

  const breadcrumbs = useClusterWebappBreadcrumbs({currentApp: 'operate'});

  const {tools, ToolsProvider} = useCamundaTools({
    notifications: IS_SAAS ? {} : undefined,
    info: {
      ariaLabel: 'Info',
      elements: getInfoSidebarItems(isPaidPlan),
    },
    user: {
      ariaLabel: 'Settings',
      version: import.meta.env.VITE_VERSION,
      name: currentUser?.displayName ?? '',
      email: currentUser?.email ?? '',
      onLogout: clientConfig.canLogout ? logoutWithNotification : undefined,
      customSection: (
        <C3ThemeSelector
          currentTheme={currentTheme.state.selectedTheme}
          onChange={(theme) =>
            currentTheme.changeTheme(theme as 'system' | 'dark' | 'light')
          }
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

                  window.Osano?.cm?.showDrawer('osano-cm-dom-info-dialog-open');
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
            tracking.track({
              eventName: 'user-side-bar',
              link: 'imprint',
            });

            window.open('https://camunda.com/legal/imprint/', '_blank');
          },
        },
      ],
    },
  });

  const sidebarChildren: SidebarNodeDescriptor[] = isForbidden(currentUser)
    ? []
    : [
        {
          type: 'item',
          key: 'dashboard',
          label: 'Dashboard',
          icon: Dashboard,
          linkProps: {to: Paths.dashboard()},
          onClick: () => {
            tracking.track({
              eventName: 'navigation',
              link: 'header-dashboard',
              currentPage,
            });
          },
        },
        {
          type: 'item',
          key: 'processes',
          label: 'Processes',
          icon: Flow,
          isActive: (active) =>
            active === 'processes' ||
            active.startsWith('process-details') === true,
          linkProps: {
            to: Locations.processes(),
            state: {refreshContent: true, hideOptionalFilters: true},
          } as never,
          onClick: () => {
            tracking.track({
              eventName: 'navigation',
              link: 'header-processes',
              currentPage,
            });
          },
        },
        {
          type: 'item',
          key: 'decisions',
          label: 'Decisions',
          icon: DecisionTree,
          isActive: (active) =>
            active === 'decisions' || active === 'decision-details',
          linkProps: {
            to: Locations.decisions(),
            state: {refreshContent: true, hideOptionalFilters: true},
          } as never,
          onClick: () => {
            tracking.track({
              eventName: 'navigation',
              link: 'header-decisions',
              currentPage,
            });
          },
        },
        {
          type: 'group',
          key: 'operations',
          label: 'Operations',
          icon: Activity,
          defaultExpanded:
            currentPage === 'batch-operations' ||
            currentPage === 'operations-log',
          children: [
            {
              type: 'item',
              key: 'batch-operations',
              label: 'Batch operations',
              icon: DataTable,
              linkProps: {to: Paths.batchOperations()},
              onClick: () => {
                tracking.track({
                  eventName: 'navigation',
                  link: 'header-batch-operations',
                  currentPage,
                });
                (document.activeElement as HTMLElement)?.blur();
              },
            },
            {
              type: 'item',
              key: 'operations-log',
              label: 'Operations log',
              icon: ListChecked,
              linkProps: {to: Paths.operationsLog()},
              onClick: () => {
                tracking.track({
                  eventName: 'navigation',
                  link: 'header-operations-log',
                  currentPage,
                });
                (document.activeElement as HTMLElement)?.blur();
              },
            },
          ],
        },
      ];

  const {navProps} = useC3NavigationV2({
    app: {
      ariaLabel: 'Camunda Operate',
      linkProps: {
        to: Paths.dashboard(),
        onClick: () => {
          tracking.track({
            eventName: 'navigation',
            link: 'header-logo',
          });
        },
      } as never,
    },
    skipToContentTargetId: SKIP_TO_CONTENT_TARGET_ID,
    activeItemKey: currentPage ?? '',
    sidebarChildren,
    breadcrumbs,
    tools,
    linkComponent: Link as never,
    headerTrailingContent: licenseTagStore.state.isTagVisible ? (
      <C3LicenseTag
        isProductionLicense={licenseTagStore.state.isProductionLicense}
        isCommercial={licenseTagStore.state.isCommercial}
        expiresAt={licenseTagStore.state.expiresAt ?? undefined}
      />
    ) : undefined,
  });

  return (
    <ToolsProvider>
      <C3NavigationV2 {...navProps} />
    </ToolsProvider>
  );
});

export {AppHeaderV2};
