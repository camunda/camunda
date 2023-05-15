/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {ReactQueryDevtools} from '@tanstack/react-query-devtools';
import {lazy, useEffect, useState, Suspense} from 'react';

const reactQueryClient = new QueryClient({
  logger: {
    log: console.log,
    warn: console.warn,
    error: process.env.NODE_ENV === 'test' ? () => {} : console.error,
  },
  defaultOptions: {
    queries: {
      retry: process.env.NODE_ENV === 'test' ? false : undefined,
    },
  },
});

const ReactQueryDevtoolsProduction: React.FC = lazy(() =>
  import('@tanstack/react-query-devtools/build/lib/index.prod.js').then(
    (module) => ({
      default: module.ReactQueryDevtools,
    }),
  ),
);

type Props = {
  children: React.ReactNode;
};

const ReactQueryProvider: React.FC<Props> = ({children}) => {
  const [isProdDevtoolsOpen, setIsProdDevtoolsOpen] = useState(false);

  useEffect(() => {
    window.toggleDevtools = () => setIsProdDevtoolsOpen((old) => !old);
  }, []);

  return (
    <QueryClientProvider client={reactQueryClient}>
      {children}
      <ReactQueryDevtools />
      {isProdDevtoolsOpen ? (
        <Suspense fallback={null}>
          <ReactQueryDevtoolsProduction />
        </Suspense>
      ) : null}
    </QueryClientProvider>
  );
};

export {ReactQueryProvider, reactQueryClient};
