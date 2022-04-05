/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {BrandInfo, Brand, UserControls, AppName, Separator} from './styled';
import {getPersistentQueryParams} from 'modules/utils/getPersistentQueryParams';
import {CmHeader, CmLogo} from '@camunda-cloud/common-ui-react';
import {LicenseNote} from './LicenseNote';
import {useLocation} from 'react-router-dom';
import {Pages} from 'modules/constants/pages';

const Header: React.FC = () => {
  const location = useLocation();

  return (
    <CmHeader>
      <BrandInfo slot="left">
        <Brand
          to={{
            ...location,
            pathname: Pages.Initial(),
            search: getPersistentQueryParams(location.search ?? ''),
          }}
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
