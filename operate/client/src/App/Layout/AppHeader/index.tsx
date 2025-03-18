/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Locations, Paths} from 'modules/Routes';
import {C3Navigation} from '@camunda/camunda-composite-components';
import {Link} from 'react-router-dom';
import {useCurrentPage} from '../useCurrentPage';
import {tracking} from 'modules/tracking';
import {authenticationStore} from 'modules/stores/authentication';
import {useEffect} from 'react';
import {ArrowRight} from '@carbon/react/icons';
import {observer} from 'mobx-react';
import {currentTheme, ThemeType} from 'modules/stores/currentTheme';
import capitalize from 'lodash/capitalize';
import {licenseTagStore} from 'modules/stores/licenseTag';

const orderedApps = [
  'console',
  'modeler',
  'tasklist',
  'operate',
  'optimize',
] as const;

const AppHeader: React.FC = observer(() => {
  const {currentPage} = useCurrentPage();
  const {displayName, canLogout, userId, salesPlanType, roles, c8Links} =
    authenticationStore.state;

  useEffect(() => {
    if (userId) {
      tracking.identifyUser({userId, salesPlanType, roles});
    }
  }, [userId, salesPlanType, roles]);

  useEffect(() => {
    licenseTagStore.fetchLicense();

    return licenseTagStore.reset;
  }, []);

  return (
    <C3Navigation
      app={{
        ariaLabel: 'Camunda Operate',
        name: 'Operate',
        routeProps: {
          to: Paths.dashboard(),
          onClick: () => {
            tracking.track({
              eventName: 'navigation',
              link: 'header-logo',
              currentPage,
            });
          },
        },
      }}
      forwardRef={Link}
      navbar={{
        elements: authenticationStore.isForbidden()
          ? []
          : [
              {
                key: 'dashboard',
                label: 'Dashboard',
                isCurrentPage: currentPage === 'dashboard',
                routeProps: {
                  to: Paths.dashboard(),
                  onClick: () => {
                    tracking.track({
                      eventName: 'navigation',
                      link: 'header-dashboard',
                      currentPage,
                    });
                  },
                },
              },
              {
                key: 'processes',
                label: 'Processes',
                isCurrentPage: currentPage === 'processes',
                routeProps: {
                  to: Locations.processes(),
                  state: {refreshContent: true, hideOptionalFilters: true},
                  onClick: () => {
                    tracking.track({
                      eventName: 'navigation',
                      link: 'header-processes',
                      currentPage,
                    });
                  },
                },
              },
              {
                key: 'decisions',
                label: 'Decisions',
                isCurrentPage: currentPage === 'decisions',
                routeProps: {
                  to: Locations.decisions(),
                  state: {refreshContent: true, hideOptionalFilters: true},
                  onClick: () => {
                    tracking.track({
                      eventName: 'navigation',
                      link: 'header-decisions',
                      currentPage,
                    });
                  },
                },
              },
            ],
        licenseTag: {
          show: licenseTagStore.state.isTagVisible,
          isProductionLicense: licenseTagStore.state.isProductionLicense,
          isCommercial: licenseTagStore.state.isCommercial,
          expiresAt: licenseTagStore.state.expiresAt,
        },
      }}
      appBar={{
        ariaLabel: 'App Panel',
        isOpen: false,
        elements: window.clientConfig?.organizationId
          ? orderedApps.map((appName) => ({
              key: appName,
              label: capitalize(appName),
              href: c8Links[appName],
              active: appName === 'operate',
              routeProps:
                appName === 'operate' ? {to: Paths.dashboard()} : undefined,
            }))
          : [],
        elementClicked: (app) => {
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
      }}
      userSideBar={{
        version: process.env.REACT_APP_VERSION,
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
            currentTheme: currentTheme.state.selectedTheme,
            onChange: (theme: string) => {
              currentTheme.changeTheme(theme as ThemeType);
            },
          },
        },
        elements: [
          ...(window.Osano?.cm !== undefined
            ? [
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
              ]
            : []),
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
        bottomElements: canLogout
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

export {AppHeader};
