/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import * as React from 'react';
import {
  HeaderContent,
  BrandInfo,
  Brand,
  LogoIcon,
  UserControls,
} from './styled';
import {getPersistentQueryParams} from 'modules/utils/getPersistentQueryParams';
import {Location} from 'history';

const Header: React.FC = () => {
  return (
    <HeaderContent>
      <BrandInfo>
        <Brand
          to={(location: Location) => ({
            ...location,
            pathname: '/',
            search: getPersistentQueryParams(location.search ?? ''),
          })}
        >
          <LogoIcon data-testid="logo" />
          <div>Tasklist</div>
        </Brand>
      </BrandInfo>
      <UserControls />
    </HeaderContent>
  );
};

export {Header};
