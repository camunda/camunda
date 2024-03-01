/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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
import {InlineLink} from './styled';

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
        elements: [
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
        tags:
          window.clientConfig?.isEnterprise === true ||
          window.clientConfig?.organizationId
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
                        >
                          terms & conditions page
                        </InlineLink>{' '}
                        or{' '}
                        <InlineLink
                          href="https://camunda.com/contact/"
                          target="_blank"
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
        elements: window.clientConfig?.organizationId
          ? orderedApps.map((appName) => ({
              key: appName,
              label: capitalize(appName),
              href: c8Links[appName],
              target: '_blank',
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
