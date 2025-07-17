/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {QueryClientProvider} from '@tanstack/react-query';
import {ReactQueryDevtools} from '@tanstack/react-query-devtools';
import {reactQueryClient} from 'modules/react-query/reactQueryClient';

type Props = {
  children: React.ReactNode;
};

const ReactQueryProvider: React.FC<Props> = ({children}) => {
  return (
    <QueryClientProvider client={reactQueryClient}>
      {children}
      <ReactQueryDevtools buttonPosition="bottom-right" />
    </QueryClientProvider>
  );
};

export {ReactQueryProvider};
