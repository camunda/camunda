/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Route, MemoryRouter, Routes} from 'react-router-dom';
import {createEnhancedIncident} from 'modules/testUtils';
import {useEffect} from 'react';
import {authenticationStore} from 'modules/stores/authentication';
import {Paths} from 'modules/Routes';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {QueryClientProvider} from '@tanstack/react-query';
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {LocationLog} from 'modules/utils/LocationLog';

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  useEffect(() => {
    return () => {
      authenticationStore.reset();
    };
  });
  return (
    <ProcessDefinitionKeyContext.Provider value="123">
      <QueryClientProvider client={getMockQueryClient()}>
        <MemoryRouter initialEntries={[Paths.processInstance('1')]}>
          <Routes>
            <Route
              path={Paths.processInstance()}
              element={
                <>
                  {children}
                  <LocationLog />
                </>
              }
            />
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

const firstIncident = createEnhancedIncident({
  errorType: 'IO_MAPPING_ERROR',
  processInstanceKey: '1',
  errorMessage: shortError,
  elementId: 'StartEvent_1',
  elementInstanceKey: '18239123812938',
  processDefinitionId: 'calledInstance',
});

const secondIncident = createEnhancedIncident({
  errorType: 'CALLED_DECISION_ERROR',
  processInstanceKey: '1',
  errorMessage: longError,
  elementId: 'Event_1db567d',
  elementInstanceKey: id,
});

const incidentsMock = [firstIncident, secondIncident];

export {Wrapper, incidentsMock, firstIncident, secondIncident};
