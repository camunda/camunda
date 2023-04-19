/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {InlineLink} from './styled';
import {Pages} from 'modules/constants/pages';
import {tracking} from 'modules/tracking';
import {authenticationStore} from 'modules/stores/authentication';
import {C3Navigation} from '@camunda/camunda-composite-components';
import {Link, matchPath, useLocation} from 'react-router-dom';
import {useEffect} from 'react';
import {useQuery} from '@apollo/client';
import {
  GetCurrentUser,
  GET_CURRENT_USER,
} from 'modules/queries/get-current-user';
import capitalize from 'lodash/capitalize';
import {ArrowRight} from '@carbon/react/icons';
import {themeStore} from 'modules/stores/theme';
import {observer} from 'mobx-react-lite';
import {usePermissions} from 'modules/hooks/usePermissions';

const orderedApps = [
  'console',
  'modeler',
  'tasklist',
  'operate',
  'optimize',
] as const;

type AppSwitcherElementType = NonNullable<
  React.ComponentProps<typeof C3Navigation>['appBar']['elements']
>[number];

const Header: React.FC = observer(() => {
  const APP_VERSION = process.env.REACT_APP_VERSION ?? '';
  const IS_SAAS = typeof window.clientConfig?.organizationId === 'string';
  const IS_ENTERPRISE = window.clientConfig?.isEnterprise === true;
  const IS_RESOURCE_PERMISSIONS_ENABLED =
    window.clientConfig?.isResourcePermissionsEnabled === true;
  const location = useLocation();
  const isProcessesPage =
    matchPath(Pages.Processes, location.pathname) !== null;
  const {data} = useQuery<GetCurrentUser>(GET_CURRENT_USER);
  const {selectedTheme, changeTheme} = themeStore;
  const {displayName, salesPlanType, c8Links} = data?.currentUser ?? {
    displayName: null,
    salesPlanType: null,
    c8Links: [],
  };
  const parsedC8Links = c8Links
    .filter(({name}) => orderedApps.includes(name))
    .reduce((acc, {name, link}) => ({...acc, [name]: link}), {}) as Record<
    (typeof orderedApps)[number],
    string
  >;
  const {hasPermission} = usePermissions(['write']);
  const switcherElements = orderedApps
    .map<AppSwitcherElementType | undefined>((appName) =>
      parsedC8Links[appName] === undefined
        ? undefined
        : {
            key: appName,
            label: capitalize(appName),
            href: parsedC8Links[appName],
            target: '_blank',
            active: appName === 'tasklist',
            routeProps:
              appName === 'tasklist' ? {to: Pages.Initial()} : undefined,
          },
    )
    .filter((entry): entry is AppSwitcherElementType => entry !== undefined);
  const isProcessesPageEnabled =
    ((!IS_SAAS && IS_RESOURCE_PERMISSIONS_ENABLED) ||
      APP_VERSION.includes('alpha') ||
      APP_VERSION.includes('SNAPSHOT')) &&
    hasPermission;
  useEffect(() => {
    if (data?.currentUser) {
      tracking.identifyUser(data?.currentUser);
    }
  }, [data]);

  return (
    <C3Navigation
      app={{
        ariaLabel: 'Camunda Tasklist',
        name: 'Tasklist',
        prefix: 'Camunda',
        routeProps: {
          to: Pages.Initial(),
          onClick: () => {
            tracking.track({
              eventName: 'navigation',
              link: 'header-logo',
            });
          },
        },
      }}
      forwardRef={Link}
      navbar={{
        elements: isProcessesPageEnabled
          ? [
              {
                isCurrentPage: !isProcessesPage,
                key: 'tasks',
                label: 'Tasks',
                routeProps: {
                  to: Pages.Initial(),
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
                  to: Pages.Processes,
                  onClick: () => {
                    tracking.track({
                      eventName: 'navigation',
                      link: 'header-processes',
                    });
                  },
                },
              },
            ]
          : [],
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
                        <InlineLink
                          href="https://camunda.com/legal/terms/camunda-platform/camunda-platform-8-self-managed/"
                          target="_blank"
                          inline
                        >
                          terms & conditions page
                        </InlineLink>{' '}
                        or{' '}
                        <InlineLink
                          href="https://camunda.com/contact/"
                          target="_blank"
                          inline
                        >
                          contact sales
                        </InlineLink>
                        .
                      </div>
                    ),
                    buttonLabel: 'Non-Production License',
                  },
                },
              ],
      }}
      appBar={{
        ariaLabel: 'App Panel',
        isOpen: false,
        elements: IS_SAAS ? switcherElements : [],
        elementClicked: (app: string) => {
          tracking.track({
            eventName: 'app-switcher-item-clicked',
            app,
          });
        },
      }}
      infoSideBar={{
        isOpen: false,
        ariaLabel: 'Info',
        elements: [
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
          {
            key: 'feedbackAndSupport',
            label: 'Feedback and Support',
            onClick: () => {
              tracking.track({
                eventName: 'info-bar',
                link: 'feedback',
              });

              if (
                salesPlanType === 'paid-cc' ||
                salesPlanType === 'enterprise'
              ) {
                window.open(
                  'https://jira.camunda.com/projects/SUPPORT/queues',
                  '_blank',
                );
              } else {
                window.open('https://forum.camunda.io/', '_blank');
              }
            },
          },
          {
            key: 'slackCommunityChannel',
            label: 'Slack Community Channel',
            onClick: () => {
              tracking.track({
                eventName: 'info-bar',
                link: 'slack',
              });

              window.open('https://camunda.com/slack', '_blank');
            },
          },
        ],
        version: process.env.REACT_APP_VERSION,
      }}
      userSideBar={{
        ariaLabel: 'Settings',
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
