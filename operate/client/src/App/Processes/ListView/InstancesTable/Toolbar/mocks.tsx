/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import {observer} from 'mobx-react';
import {MemoryRouter} from 'react-router-dom';
import {Screen, waitFor} from '@testing-library/react';
import {Paths} from 'modules/Routes';
import {ProcessInstancesDto} from 'modules/api/processInstances/fetchProcessInstances';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {processInstancesStore} from 'modules/stores/processInstances';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelection';
import {processStatisticsStore} from 'modules/stores/processStatistics/processStatistics.migration.source';
import {UserEvent} from '@testing-library/user-event';
import {batchModificationStore} from 'modules/stores/batchModification';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {ProcessDefinitionKeyContext} from '../../processDefinitionKeyContext';

const fetchProcessInstances = async (screen: Screen, user: UserEvent) => {
  await user.click(
    screen.getByRole('button', {name: /fetch process instances/i}),
  );
  await waitFor(() =>
    expect(processInstancesStore.state.status).toBe('fetched'),
  );
};

const getProcessInstance = (
  state: ProcessInstanceEntity['state'],
  mockData: ProcessInstancesDto,
) => {
  const instance = mockData.processInstances.find(
    (instance) => instance.state === state,
  );

  if (instance === undefined) {
    throw new Error(`please make sure there is a ${state} in mockData`);
  }

  return instance;
};

function getWrapper(initialPath: string = Paths.processes()) {
  const Wrapper: React.FC<{children?: React.ReactNode}> = observer(
    ({children}) => {
      useEffect(() => {
        return () => {
          processInstancesSelectionStore.reset();
          processInstancesStore.reset();
          processInstanceMigrationStore.reset();
          processStatisticsStore.reset();
          batchModificationStore.reset();
        };
      }, []);
      return (
        <ProcessDefinitionKeyContext.Provider value="123">
          <QueryClientProvider client={new QueryClient()}>
            <MemoryRouter initialEntries={[initialPath]}>
              {children}
              <button
                onClick={
                  processInstancesSelectionStore.selectAllProcessInstances
                }
              >
                Select all instances
              </button>
              <button
                onClick={() =>
                  processInstancesStore.fetchInstances({
                    fetchType: 'initial',
                    payload: {query: {}},
                  })
                }
              >
                Fetch process instances
              </button>
              <button onClick={batchModificationStore.enable}>
                Enter batch modification mode
              </button>
              <button onClick={batchModificationStore.reset}>
                Exit batch modification mode
              </button>
            </MemoryRouter>
          </QueryClientProvider>
        </ProcessDefinitionKeyContext.Provider>
      );
    },
  );

  return Wrapper;
}

export {getWrapper, getProcessInstance, fetchProcessInstances};
