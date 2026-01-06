/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Route, MemoryRouter, Routes} from 'react-router-dom';
import {LocationLog} from 'modules/utils/LocationLog';
import {createIncident} from 'modules/testUtils';
import {Paths} from 'modules/Routes';
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import type {QueryProcessInstanceIncidentsResponseBody} from '@camunda/camunda-api-zod-schemas/8.8';

const firstIncident = createIncident({
  errorType: 'CONDITION_ERROR',
  creationTime: '2022-03-01T14:26:19',
  elementId: 'flowNodeId_exclusiveGateway',
});

const secondIncident = createIncident({
  errorType: 'EXTRACT_VALUE_ERROR',
  elementId: 'flowNodeId_alwaysFailingTask',
});

const mockIncidents: QueryProcessInstanceIncidentsResponseBody = {
  page: {totalItems: 2},
  items: [firstIncident, secondIncident],
};

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
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

export {mockIncidents, firstIncident, secondIncident, Wrapper};
