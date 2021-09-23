/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

/* istanbul ignore file */

import React from 'react';
import styled from 'styled-components';

import {BrandInfo, Brand, AppName} from './styled';
import {Dropdown} from './Dropdown';
import {MockedApolloProvider} from 'modules/mock-schema/MockedApolloProvider';
import {mockGetCurrentUser} from 'modules/queries/get-current-user';
import {CmLogo} from '@camunda-cloud/common-ui-react';

const Wrapper = styled.div`
  width: 100%;
  display: flex;
  justify-content: center;
`;

export default {
  title: 'Components/Tasklist/Header',
};

const Logo: React.FC = () => {
  return (
    <BrandInfo>
      <Brand to="/">
        <CmLogo data-testid="logo" />
        <AppName>Tasklist</AppName>
      </Brand>
    </BrandInfo>
  );
};

const UserDropdown: React.FC = () => {
  return (
    <MockedApolloProvider mocks={[mockGetCurrentUser]}>
      <Wrapper>
        <Dropdown />
      </Wrapper>
    </MockedApolloProvider>
  );
};

const UserDropdownOpen: React.FC = () => {
  return (
    <MockedApolloProvider mocks={[mockGetCurrentUser]}>
      <Wrapper>
        <Dropdown isInitiallyOpen />
      </Wrapper>
    </MockedApolloProvider>
  );
};

export {Logo, UserDropdown, UserDropdownOpen};
