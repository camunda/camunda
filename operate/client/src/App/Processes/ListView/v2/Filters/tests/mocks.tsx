/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Route, MemoryRouter, Routes} from 'react-router-dom';
import {LocationLog} from 'modules/utils/LocationLog';
import {Paths} from 'modules/Routes';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';

function getWrapper(initialPath: string = Paths.dashboard()) {
  const MockApp: React.FC<{children?: React.ReactNode}> = ({children}) => {
    return (
      <ProcessDefinitionKeyContext.Provider value="123">
        <QueryClientProvider client={new QueryClient()}>
          <MemoryRouter initialEntries={[initialPath]}>
            <Routes>
              <Route path={Paths.dashboard()} element={children} />
            </Routes>
            <LocationLog />
          </MemoryRouter>
        </QueryClientProvider>
      </ProcessDefinitionKeyContext.Provider>
    );
  };

  return MockApp;
}

export {getWrapper};
