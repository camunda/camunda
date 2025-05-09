/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {createOperation} from 'modules/testUtils';
import {modificationsStore} from 'modules/stores/modifications';
import {processInstancesStore} from 'modules/stores/processInstances';
import {LocationLog} from 'modules/utils/LocationLog';
import {Paths} from 'modules/Routes';
import {ProcessInstance} from '@vzeta/camunda-api-zod-schemas/operate';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {QueryClientProvider} from '@tanstack/react-query';
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';

const getWrapper = (
  initialEntries: React.ComponentProps<
    typeof MemoryRouter
  >['initialEntries'] = [Paths.processInstance('1')],
) => {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    useEffect(() => {
      return () => {
        modificationsStore.reset();
        processInstancesStore.reset();
      };
    }, []);

    return (
      <ProcessDefinitionKeyContext.Provider value="123">
        <QueryClientProvider client={getMockQueryClient()}>
          <MemoryRouter initialEntries={initialEntries}>
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
  return Wrapper;
};

const FAILED_OPERATION = createOperation({state: 'FAILED'});
const SENT_OPERATION = createOperation({state: 'SENT'});
const INSTANCE: ProcessInstance = {
  processInstanceKey: 'instance_1',
  state: 'ACTIVE',
  processDefinitionVersion: 1,
  processDefinitionId: 'someKey',
  processDefinitionKey: '2',
  tenantId: '<default>',
  processDefinitionName: 'someProcessName',
  startDate: '2018-06-21',
  hasIncident: false,
};
const ACTIVE_INSTANCE: ProcessInstance = {
  ...INSTANCE,
  state: 'ACTIVE',
};

export {
  getWrapper,
  ACTIVE_INSTANCE,
  INSTANCE,
  FAILED_OPERATION,
  SENT_OPERATION,
};
