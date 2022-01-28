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
import {IS_DMN} from 'modules/feature-flags';

const Header: React.FC = observer(() => {
  return (
    <CmHeader>
      <nav slot="left">
        <Menu>
          <NavElement
            to={Locations.dashboard}
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
            to={Locations.dashboard}
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
            to={Locations.filters}
            title="View Instances"
            label="Instances"
            onClick={() => {
              tracking.track({
                eventName: 'navigation',
                link: 'header-instances',
              });
            }}
          />
          {IS_DMN && (
            <NavElement
              to={Locations.decisions}
              title="View Decisions"
              label="Decisions"
            />
          )}
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
