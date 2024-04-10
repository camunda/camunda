/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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

import {useEffect, useState} from 'react';
import capitalize from 'lodash/capitalize';
import {observer} from 'mobx-react-lite';
import {Link as RouterLink, matchPath, useLocation} from 'react-router-dom';
import {Link} from '@carbon/react';
import {ArrowRight} from '@carbon/react/icons';
import {C3Navigation} from '@camunda/camunda-composite-components';
import {TermsConditionsModal} from 'modules/components/TermsConditionsModal';
import {pages} from 'modules/routing';
import {tracking} from 'modules/tracking';
import {authenticationStore} from 'modules/stores/authentication';
import {themeStore} from 'modules/stores/theme';
import {useCurrentUser} from 'modules/queries/useCurrentUser';
import {getStateLocally} from 'modules/utils/localStorage';
import styles from './styles.module.scss';

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
  const IS_SAAS = typeof window.clientConfig?.organizationId === 'string';
  const IS_ENTERPRISE = window.clientConfig?.isEnterprise === true;
  const location = useLocation();
  const isProcessesPage =
    matchPath(pages.processes(), location.pathname) !== null;
  const {data: currentUser} = useCurrentUser();
  const {selectedTheme, changeTheme} = themeStore;
  const {displayName, salesPlanType, c8Links} = currentUser ?? {
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
  const switcherElements = orderedApps
    .map<AppSwitcherElementType | undefined>((appName) =>
      parsedC8Links[appName] === undefined
        ? undefined
        : {
            key: appName,
            label: capitalize(appName),
            href: parsedC8Links[appName],
            active: appName === 'tasklist',
            routeProps:
              appName === 'tasklist' ? {to: pages.initial} : undefined,
          },
    )
    .filter((entry): entry is AppSwitcherElementType => entry !== undefined);

  const [isTermsConditionModalOpen, setTermsConditionModalOpen] =
    useState(false);

  useEffect(() => {
    if (currentUser) {
      tracking.identifyUser(currentUser);
    }
  }, [currentUser]);

  return (
    <>
      <C3Navigation
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
                          Non-Production License. If you would like information
                          on production usage, please refer to our{' '}
                          <Link
                            className={styles.inlineLink}
                            href="#"
                            onClick={(event) => {
                              event.preventDefault();
                              setTermsConditionModalOpen(true);
                            }}
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
      {IS_ENTERPRISE || IS_SAAS ? null : (
        <TermsConditionsModal
          isModalOpen={isTermsConditionModalOpen}
          onModalClose={() => setTermsConditionModalOpen(false)}
        />
      )}
    </>
  );
});

export {Header};
