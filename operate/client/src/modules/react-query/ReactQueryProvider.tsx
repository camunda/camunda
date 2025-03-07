/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {ReactQueryDevtools} from '@tanstack/react-query-devtools';
import {lazy, useEffect, useState, Suspense} from 'react';

declare global {
  interface Window {
    toggleDevtools: () => void;
  }
}

const ReactQueryDevtoolsProduction = lazy(() =>
  import('@tanstack/react-query-devtools/build/modern/production.js').then(
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
    <QueryClientProvider client={new QueryClient()}>
      {children}
      <ReactQueryDevtools buttonPosition="bottom-right" />
      {isProdDevtoolsOpen ? (
        <Suspense fallback={null}>
          <ReactQueryDevtoolsProduction buttonPosition="bottom-right" />
        </Suspense>
      ) : null}
    </QueryClientProvider>
  );
};

export {ReactQueryProvider};
