/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {User} from './User';
import {NavElement} from './NavElement';
import {Menu, LeftSeparator, RightSeparator} from './styled';
import {Locations, Paths} from 'modules/routes';
import {CmHeader, CmLogo} from '@camunda-cloud/common-ui-react';
import {LicenseNote} from './LicenseNote';

const Header: React.FC = () => {
  return (
    <CmHeader>
      <nav slot="left">
        <Menu>
          <NavElement
            to={Paths.dashboard()}
            title="View Dashboard"
            label="Operate"
            icon={<CmLogo />}
            trackingEvent="header-logo"
          />
          <LeftSeparator />
          <NavElement
            to={Paths.dashboard()}
            title="View Dashboard"
            label="Dashboard"
            trackingEvent="header-dashboard"
          />
          <NavElement
            to={Locations.processes()}
            title="View Processes"
            label="Processes"
            state={{hideOptionalFilters: true}}
            trackingEvent="header-processes"
          />
          <NavElement
            to={Locations.decisions()}
            title="View Decisions"
            label="Decisions"
            state={{hideOptionalFilters: true}}
            trackingEvent="header-decisions"
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
};

export {Header};
