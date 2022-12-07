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
import {Link} from 'react-router-dom';
import {useEffect} from 'react';
import {useQuery} from '@apollo/client';
import {
  GetCurrentUser,
  GET_CURRENT_USER,
} from 'modules/queries/get-current-user';
import {capitalize} from 'lodash';
import {ArrowRight} from '@carbon/react/icons';

const orderedApps = [
  'console',
  'modeler',
  'tasklist',
  'operate',
  'optimize',
] as const;

const Header: React.FC = () => {
  const {data} = useQuery<GetCurrentUser>(GET_CURRENT_USER);
  const {displayName, salesPlanType, c8Links} = data?.currentUser ?? {
    displayName: null,
    salesPlanType: null,
    c8Links: [],
  };
  const parsedC8Links = c8Links
    .filter(({name}) => orderedApps.includes(name))
    .reduce((acc, {name, link}) => ({...acc, [name]: link}), {}) as Record<
    typeof orderedApps[number],
    string
  >;

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
        elements: [],
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
        type: 'app',
        ariaLabel: 'App Panel',
        isOpen: false,
        elements: window.clientConfig?.organizationId
          ? orderedApps.map((appName) => ({
              key: appName,
              label: capitalize(appName),
              href: parsedC8Links[appName],
              target: '_blank',
              active: appName === 'tasklist',
              routeProps:
                appName === 'tasklist' ? {to: Pages.Initial()} : undefined,
            }))
          : [],
        elementClicked: (app: string) => {
          tracking.track({
            eventName: 'app-switcher-item-clicked',
            app,
          });
        },
      }}
      infoSideBar={{
        type: 'info',
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

              window.open(
                'https://camunda-slack-invite.herokuapp.com/',
                '_blank',
              );
            },
          },
        ],
      }}
      userSideBar={{
        type: 'user',
        ariaLabel: 'Settings',
        customElements: {
          profile: {
            label: 'Profile',
            user: {
              name: displayName ?? '',
              email: '',
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
};

export {Header};
