/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Container, Link, Label} from './styled';
import {NavLink} from 'react-router-dom';
import {tracking} from 'modules/tracking';
import {useCurrentPage} from '../../useCurrentPage';

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
  const {currentPage} = useCurrentPage();

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
            currentPage,
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
