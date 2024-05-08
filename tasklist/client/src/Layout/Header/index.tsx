/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import {observer} from 'mobx-react-lite';
import {Link as RouterLink, matchPath, useLocation} from 'react-router-dom';
import {Link} from '@carbon/react';
import {ArrowRight} from '@carbon/react/icons';
import {C3Navigation} from '@camunda/camunda-composite-components';
import {pages} from 'modules/routing';
import {tracking} from 'modules/tracking';
import {authenticationStore} from 'modules/stores/authentication';
import {themeStore} from 'modules/stores/theme';
import {useCurrentUser} from 'modules/queries/useCurrentUser';
import {getStateLocally} from 'modules/utils/localStorage';
import styles from './styles.module.scss';

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

const Header: React.FC = observer(() => {
  const IS_SAAS = typeof window.clientConfig?.organizationId === 'string';
  const IS_ENTERPRISE = window.clientConfig?.isEnterprise === true;
  const location = useLocation();
  const isProcessesPage =
    matchPath(pages.processes(), location.pathname) !== null;
  const {data: currentUser} = useCurrentUser();
  const {selectedTheme, changeTheme} = themeStore;
  const {displayName, salesPlanType} = currentUser ?? {
    displayName: null,
    salesPlanType: null,
  };

  useEffect(() => {
    if (currentUser) {
      tracking.identifyUser(currentUser);
    }
  }, [currentUser]);

  return (
    <C3Navigation
      notificationSideBar={IS_SAAS ? {} : undefined}
      appBar={{
        ariaLabel: 'App Panel',
        isOpen: false,
        elementClicked: (app: string) => {
          tracking.track({
            eventName: 'app-switcher-item-clicked',
            app,
          });
        },
        appTeaserRouteProps: IS_SAAS ? {} : undefined,
      }}
      app={{
        ariaLabel: 'Camunda Tasklist',
        name: 'Tasklist',
        routeProps: {
          to: pages.initial,
          onClick: () => {
            tracking.track({
              eventName: 'navigation',
              link: 'header-logo',
            });
          },
        },
      }}
      forwardRef={RouterLink}
      navbar={{
        elements: [
          {
            isCurrentPage: !isProcessesPage,
            key: 'tasks',
            label: 'Tasks',
            routeProps: {
              to: pages.initial,
              onClick: () => {
                tracking.track({
                  eventName: 'navigation',
                  link: 'header-tasks',
                });
              },
            },
          },
          {
            isCurrentPage: isProcessesPage,
            key: 'processes',
            label: 'Processes',
            routeProps: {
              to: pages.processes({
                tenantId: getStateLocally('tenantId') ?? undefined,
              }),
              onClick: () => {
                tracking.track({
                  eventName: 'navigation',
                  link: 'header-processes',
                });
              },
            },
          },
        ],
        tags:
          IS_ENTERPRISE || IS_SAAS
            ? []
            : [
                {
                  key: 'non-production-license',
                  label: 'Non-Production License',
                  color: 'cool-gray',
                  tooltip: {
                    content: (
                      <div>
                        Non-Production License. If you would like information on
                        production usage, please refer to our{' '}
                        <Link
                          className={styles.inlineLink}
                          href="https://legal.camunda.com/#self-managed-non-production-terms"
                          target="_blank"
                          inline
                        >
                          terms & conditions page
                        </Link>{' '}
                        or{' '}
                        <Link
                          className={styles.inlineLink}
                          href="https://camunda.com/contact/"
                          target="_blank"
                          inline
                        >
                          contact sales
                        </Link>
                        .
                      </div>
                    ),
                    buttonLabel: 'Non-Production License',
                  },
                },
              ],
      }}
      infoSideBar={{
        isOpen: false,
        ariaLabel: 'Info',
        elements: getInfoSidebarItems(
          ['paid-cc', 'enterprise'].includes(salesPlanType!),
        ),
      }}
      userSideBar={{
        ariaLabel: 'Settings',
        version: import.meta.env.VITE_VERSION,
        customElements: {
          profile: {
            label: 'Profile',
            user: {
              name: displayName ?? '',
              email: '',
            },
          },
          themeSelector: {
            currentTheme: selectedTheme,
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
              tracking.track({
                eventName: 'user-side-bar',
                link: 'imprint',
              });

              window.open('https://camunda.com/legal/imprint/', '_blank');
            },
          },
        ],
        bottomElements: window.clientConfig?.canLogout
          ? [
              {
                key: 'logout',
                label: 'Log out',
                renderIcon: ArrowRight,
                kind: 'ghost',
                onClick: authenticationStore.handleLogout,
              },
            ]
          : undefined,
      }}
    />
  );
});

export {Header};
