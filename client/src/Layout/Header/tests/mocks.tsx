/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/getMockQueryClient';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {MemoryRouter} from 'react-router-dom';

const getWrapper = () => {
  const mockClient = getMockQueryClient();

  type Props = {
    children?: React.ReactNode;
  };

  const Wrapper: React.FC<Props> = ({children}) => (
    <QueryClientProvider client={mockClient}>
      <MockThemeProvider>
        <MemoryRouter initialEntries={['/']}>{children}</MemoryRouter>
      </MockThemeProvider>
    </QueryClientProvider>
  );
  return Wrapper;
};

export {getWrapper};
