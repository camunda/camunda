/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import * as React from 'react';
import {BrandInfo, Brand, UserControls, AppName, Separator} from './styled';
import {getPersistentQueryParams} from 'modules/utils/getPersistentQueryParams';
import {Location} from 'history';
import {CmHeader, CmLogo} from '@camunda-cloud/common-ui-react';
import {LicenseNote} from './LicenseNote';

const Header: React.FC = () => {
  return (
    <CmHeader>
      <BrandInfo slot="left">
        <Brand
          to={(location: Location) => ({
            ...location,
            pathname: '/',
            search: getPersistentQueryParams(location.search ?? ''),
          })}
        >
          <CmLogo data-testid="logo" />
          <AppName>Tasklist</AppName>
        </Brand>
      </BrandInfo>
      {window.clientConfig?.isEnterprise === true ||
      window.clientConfig?.organizationId ? null : (
        <>
          <div slot="right">
            <LicenseNote />
          </div>
          <div slot="right">
            <Separator />
          </div>
        </>
      )}
      <UserControls slot="right" />
    </CmHeader>
  );
};

export {Header};
