/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {MemoryRouter} from 'react-router-dom';
import {LocationLog} from 'modules/utils/LocationLog';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {QueryClientProvider} from '@tanstack/react-query';

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => (
  <QueryClientProvider client={getMockQueryClient()}>
    <MemoryRouter>
      {children}
      <LocationLog />
    </MemoryRouter>
  </QueryClientProvider>
);

export {Wrapper};
