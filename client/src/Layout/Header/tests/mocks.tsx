/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ApolloProvider} from '@apollo/client';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {client} from 'modules/apollo-client';
import {MemoryRouter} from 'react-router-dom';

type Props = {
  children?: React.ReactNode;
};

const Wrapper: React.FC<Props> = ({children}) => (
  <ApolloProvider client={client}>
    <MockThemeProvider>
      <MemoryRouter initialEntries={['/']}>{children}</MemoryRouter>
    </MockThemeProvider>
  </ApolloProvider>
);

export {Wrapper};
