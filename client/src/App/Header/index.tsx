/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState} from 'react';
import {Redirect, useLocation} from 'react-router-dom';
import {observer} from 'mobx-react';
import {User} from './User';
import {NavElement} from './NavElement';
import {Menu, Separator} from './styled';
import {Locations} from 'modules/routes';
import {CmHeader, CmLogo} from '@camunda-cloud/common-ui-react';

const Header: React.FC = observer(() => {
  const [forceRedirect, setForceRedirect] = useState(false);
  const location = useLocation();

  if (forceRedirect) {
    return <Redirect to={Locations.login(location)} />;
  }

  return (
    <CmHeader>
      <nav slot="left">
        <Menu>
          <NavElement
            to={Locations.dashboard}
            title="View Dashboard"
            label="Operate"
            icon={<CmLogo />}
          />
          <Separator />
          <NavElement
            to={Locations.dashboard}
            title="View Dashboard"
            label="Dashboard"
          />
          <NavElement
            to={Locations.filters}
            title="View Instances"
            label="Instances"
          />
        </Menu>
      </nav>
      <User handleRedirect={() => setForceRedirect(true)} slot="right" />
    </CmHeader>
  );
});

export {Header};
