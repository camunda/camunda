/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Route, MemoryRouter, Routes} from 'react-router-dom';
import {createIncident} from 'modules/testUtils';
import {useEffect} from 'react';
import {authenticationStore} from 'modules/stores/authentication';
import {incidentsStore} from 'modules/stores/incidents';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {Paths} from 'modules/Routes';
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  useEffect(() => {
    return () => {
      incidentsStore.reset();
      authenticationStore.reset();
      flowNodeSelectionStore.reset();
    };
  });
  return (
    <ProcessDefinitionKeyContext.Provider value="123">
      <QueryClientProvider client={getMockQueryClient()}>
        <MemoryRouter initialEntries={[Paths.processInstance('1')]}>
          <Routes>
            <Route path={Paths.processInstance()} element={children} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    </ProcessDefinitionKeyContext.Provider>
  );
};

const id = 'flowNodeInstanceIdB';
const shortError = 'No data found for query $.orderId.';
const longError =
  'Cannot compare values of different types: INTEGER and BOOLEAN';

const firstIncident = createIncident({
  errorType: {name: 'Error A', id: 'ERROR_A'},
  errorMessage: shortError,
  flowNodeId: 'StartEvent_1',
  flowNodeInstanceId: '18239123812938',
  rootCauseInstance: {
    instanceId: '111111111111111111',
    processDefinitionId: 'calledInstance',
    processDefinitionName: 'Called Instance',
  },
});

const secondIncident = createIncident({
  errorType: {name: 'Error B', id: 'ERROR_A'},
  errorMessage: longError,
  flowNodeId: 'Event_1db567d',
  flowNodeInstanceId: id,
});

const incidentsMock = {
  incidents: [firstIncident, secondIncident],
  count: 2,
  errorTypes: [],
  flowNodes: [],
};

export {Wrapper, incidentsMock, firstIncident, secondIncident, shortError};
