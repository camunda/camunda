/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Locations, Paths} from 'modules/routes';
import {C3Navigation} from '@camunda/camunda-composite-components';
import {Link} from 'react-router-dom';
import {useCurrentPage} from '../useCurrentPage';
import {tracking} from 'modules/tracking';
import {authenticationStore} from 'modules/stores/authentication';
import {useEffect} from 'react';
import {ArrowRight} from '@carbon/react/icons';
import {observer} from 'mobx-react';

const AppHeader: React.FC = observer(() => {
  const {currentPage} = useCurrentPage();
  const {displayName, canLogout, userId, salesPlanType, roles} =
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
        prefix: 'Camunda',
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
      }}
      appBar={{
        type: 'app',
        ariaLabel: 'App Panel',
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
