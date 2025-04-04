/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {MemoryRouter} from 'react-router-dom';
import {useEffect} from 'react';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelection';
import {processInstancesStore} from 'modules/stores/processInstances';
import {operationsStore} from 'modules/stores/operations';
import {processesStore} from 'modules/stores/processes/processes.list';
import {processStatisticsStore} from 'modules/stores/processStatistics/processStatistics.list';
import {Paths} from 'modules/Routes';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';

function createWrapper(initialPath: string = Paths.dashboard()) {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    useEffect(() => {
      return () => {
        processInstancesSelectionStore.reset();
        processInstancesStore.reset();
        processStatisticsStore.reset();
        operationsStore.reset();
        processesStore.reset();
      };
    }, []);

    return (
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter initialEntries={[initialPath]}>{children}</MemoryRouter>
      </QueryClientProvider>
    );
  };

  return Wrapper;
}

export {createWrapper};
