/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Paths} from 'modules/Routes';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {incidentsStore} from 'modules/stores/incidents';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {LocationLog} from 'modules/utils/LocationLog';
import {useEffect} from 'react';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {MetadataPopover} from '.';
import {ProcessInstance} from 'modules/testUtils/pages/ProcessInstance';
import {render} from 'modules/testing-library';
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  useEffect(() => {
    return () => {
      flowNodeMetaDataStore.reset();
      flowNodeSelectionStore.reset();
      processInstanceDetailsStore.reset();
      incidentsStore.reset();
    };
  }, []);

  return (
    <ProcessDefinitionKeyContext.Provider value="123">
      <QueryClientProvider client={getMockQueryClient()}>
        <MemoryRouter initialEntries={[Paths.processInstance('1')]}>
          <Routes>
            <Route path={Paths.processInstance()} element={children} />
            <Route path={Paths.decisionInstance()} element={<></>} />
          </Routes>
          <LocationLog />
        </MemoryRouter>
      </QueryClientProvider>
    </ProcessDefinitionKeyContext.Provider>
  );
};

const renderPopover = () => {
  const {container} = render(<svg />);

  return render(
    <MetadataPopover selectedFlowNodeRef={container.querySelector('svg')} />,
    {
      wrapper: Wrapper,
    },
  );
};

const {
  metadataPopover: {labels},
} = new ProcessInstance();

export {labels, renderPopover};
