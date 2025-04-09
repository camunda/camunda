/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {authenticationStore} from 'modules/stores/authentication';
import {operationsStore} from 'modules/stores/operations';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {variablesStore} from 'modules/stores/variables';
import {createBatchOperation, createInstance} from 'modules/testUtils';
import {useEffect} from 'react';
import {Paths} from 'modules/Routes';
import {LocationLog} from 'modules/utils/LocationLog';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';

const mockOperationCreated = createBatchOperation();

const mockInstanceWithActiveOperation = createInstance({
  hasActiveOperation: true,
});

const mockCanceledInstance = createInstance({
  state: 'CANCELED',
});

const mockInstanceWithParentInstance = createInstance({
  parentInstanceId: '8724390842390124',
});

const mockInstanceWithoutOperations = createInstance({
  operations: [],
});

const mockProcess = {
  id: '2251799813688076',
  name: 'Complex Process',
  version: 3,
  bpmnProcessId: 'complexProcess',
  versionTag: 'myVersionTag',
};

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  useEffect(() => {
    return () => {
      operationsStore.reset();
      variablesStore.reset();
      processInstanceDetailsStore.reset();
      processInstanceDetailsDiagramStore.reset();
      authenticationStore.reset();
    };
  }, []);

  return (
    <ProcessDefinitionKeyContext.Provider value="123">
      <QueryClientProvider client={getMockQueryClient()}>
        <MemoryRouter initialEntries={[Paths.processInstance('1')]}>
          <Routes>
            <Route path={Paths.processInstance()} element={children} />
            <Route path={Paths.processes()} element={children} />
          </Routes>
          <LocationLog />
        </MemoryRouter>
      </QueryClientProvider>
    </ProcessDefinitionKeyContext.Provider>
  );
};

export {
  mockOperationCreated,
  mockInstanceWithActiveOperation,
  mockCanceledInstance,
  mockInstanceWithParentInstance,
  mockInstanceWithoutOperations,
  mockProcess,
  Wrapper,
};
