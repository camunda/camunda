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
import {batchModificationStore} from 'modules/stores/batchModification';
import {Paths} from 'modules/Routes';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import type {ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.8';
import {ProcessDefinitionKeyContext} from '../../processDefinitionKeyContext';
import {buildV2ProcessInstanceData} from 'modules/utils/processInstance/processInstanceDataBuilder';

type WrapperOptions = {
  initialPath?: string;
  processInstances?: ProcessInstance[];
};

function createWrapper(options: WrapperOptions | string = {}) {
  const {initialPath = Paths.dashboard(), processInstances = []} =
    typeof options === 'string' ? {initialPath: options} : options;

  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    useEffect(() => {
      processInstancesSelectionStore.init();
      if (processInstances.length > 0) {
        const adaptedInstances = processInstances.map(
          buildV2ProcessInstanceData,
        );
        processInstancesStore.setProcessInstances({
          processInstances: adaptedInstances,
          filteredProcessInstancesCount: processInstances.length,
        });
      }

      return () => {
        processInstancesSelectionStore.reset();
        processInstancesStore.reset();
        operationsStore.reset();
        batchModificationStore.reset();
      };
    }, []);

    return (
      <QueryClientProvider
        client={
          new QueryClient({
            defaultOptions: {
              queries: {
                retry: false,
              },
            },
          })
        }
      >
        <MemoryRouter initialEntries={[initialPath]}>
          <ProcessDefinitionKeyContext.Provider value="2251799813685592">
            {children}
            <button onClick={batchModificationStore.enable}>
              Enable batch modification mode
            </button>
          </ProcessDefinitionKeyContext.Provider>
        </MemoryRouter>
      </QueryClientProvider>
    );
  };

  return Wrapper;
}

export {createWrapper};
