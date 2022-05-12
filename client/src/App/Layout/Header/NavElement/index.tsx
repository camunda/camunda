/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Container, Link, Label} from './styled';
import {NavLink, useLocation, matchPath} from 'react-router-dom';
import {tracking} from 'modules/tracking';
import {Paths} from 'modules/routes';

type Props = {
  to: React.ComponentProps<typeof NavLink>['to'];
  state?: React.ComponentProps<typeof NavLink>['state'];
  icon?: React.ReactNode;
  title: string;
  label: string;
  trackingEvent:
    | 'header-logo'
    | 'header-dashboard'
    | 'header-processes'
    | 'header-decisions';
};

const NavElement: React.FC<Props> = ({
  title,
  to,
  icon,
  label,
  trackingEvent,
  state,
}) => {
  const location = useLocation();

  function getCurrentPage():
    | 'dashboard'
    | 'processes'
    | 'decisions'
    | 'process-details'
    | 'decision-details'
    | 'login'
    | undefined {
    if (matchPath(Paths.dashboard(), location.pathname) !== null) {
      return 'dashboard';
    }

    if (matchPath(Paths.processes(), location.pathname) !== null) {
      return 'processes';
    }

    if (matchPath(Paths.decisions(), location.pathname) !== null) {
      return 'decisions';
    }

    if (matchPath(Paths.processInstance(), location.pathname) !== null) {
      return 'process-details';
    }

    if (matchPath(Paths.decisionInstance(), location.pathname) !== null) {
      return 'decision-details';
    }

    return;
  }

  return (
    <Container>
      <Link
        caseSensitive
        className={({isActive}) => (isActive ? 'active' : '')}
        title={title}
        to={to}
        end
        onClick={() => {
          tracking.track({
            eventName: 'navigation',
            link: trackingEvent,
            currentPage: getCurrentPage(),
          });
        }}
        state={state}
      >
        {icon}
        <Label>{label}</Label>
      </Link>
    </Container>
  );
};

export {NavElement};
