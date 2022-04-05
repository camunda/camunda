/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {observer} from 'mobx-react';
import {User} from './User';
import {NavElement} from './NavElement';
import {Menu, LeftSeparator, RightSeparator} from './styled';
import {Locations} from 'modules/routes';
import {CmHeader, CmLogo} from '@camunda-cloud/common-ui-react';
import {tracking} from 'modules/tracking';
import {LicenseNote} from './LicenseNote';
import {useLocation} from 'react-router-dom';

const Header: React.FC = observer(() => {
  const location = useLocation();

  return (
    <CmHeader>
      <nav slot="left">
        <Menu>
          <NavElement
            to={Locations.dashboard(location)}
            title="View Dashboard"
            label="Operate"
            icon={<CmLogo />}
            onClick={() => {
              tracking.track({
                eventName: 'navigation',
                link: 'header-logo',
              });
            }}
          />
          <LeftSeparator />
          <NavElement
            to={Locations.dashboard(location)}
            title="View Dashboard"
            label="Dashboard"
            onClick={() => {
              tracking.track({
                eventName: 'navigation',
                link: 'header-dashboard',
              });
            }}
          />
          <NavElement
            to={Locations.filters(location)}
            title="View Processes"
            label="Processes"
            onClick={() => {
              tracking.track({
                eventName: 'navigation',
                link: 'header-processes',
              });
            }}
          />
          <NavElement
            to={Locations.decisions(location)}
            title="View Decisions"
            label="Decisions"
            onClick={() => {
              tracking.track({
                eventName: 'navigation',
                link: 'header-decisions',
              });
            }}
          />
        </Menu>
      </nav>
      {window.clientConfig?.isEnterprise === true ||
      window.clientConfig?.organizationId ? null : (
        <>
          <div slot="right">
            <LicenseNote />
          </div>
          <div slot="right">
            <RightSeparator />
          </div>
        </>
      )}
      <div slot="right">
        <User />
      </div>
    </CmHeader>
  );
});

export {Header};
