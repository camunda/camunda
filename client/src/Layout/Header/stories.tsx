/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

/* istanbul ignore file */

import styled from 'styled-components';
import {ComponentStory, ComponentMeta} from '@storybook/react';
import {BrandInfo, Brand, AppName} from './styled';
import {Dropdown} from './Dropdown';
import {MockedApolloProvider} from 'modules/mock-schema/MockedApolloProvider';
import {mockGetCurrentUser} from 'modules/queries/get-current-user';
import {CmLogo} from '@camunda-cloud/common-ui-react';
import {MemoryRouter} from 'react-router-dom';

const Wrapper = styled.div`
  width: 100%;
  display: flex;
  justify-content: center;
`;

export default {
  title: 'Components/Tasklist/Header',
  decorators: [
    (Story) => (
      <MemoryRouter>
        <Story />
      </MemoryRouter>
    ),
  ],
} as ComponentMeta<React.FC>;

const Logo: ComponentStory<React.FC> = () => {
  return (
    <BrandInfo>
      <Brand to="/">
        <CmLogo data-testid="logo" />
        <AppName>Tasklist</AppName>
      </Brand>
    </BrandInfo>
  );
};

const UserDropdown: ComponentStory<React.FC> = () => {
  return (
    <MockedApolloProvider mocks={[mockGetCurrentUser]}>
      <Wrapper>
        <Dropdown />
      </Wrapper>
    </MockedApolloProvider>
  );
};

const UserDropdownOpen: ComponentStory<React.FC> = () => {
  return (
    <MockedApolloProvider mocks={[mockGetCurrentUser]}>
      <Wrapper>
        <Dropdown isInitiallyOpen />
      </Wrapper>
    </MockedApolloProvider>
  );
};

export {Logo, UserDropdown, UserDropdownOpen};
