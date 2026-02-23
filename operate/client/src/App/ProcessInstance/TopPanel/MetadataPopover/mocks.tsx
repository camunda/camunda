/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Paths} from 'modules/Routes';
import {LocationLog} from 'modules/utils/LocationLog';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {MetadataPopover} from '.';
import {ProcessInstance} from 'modules/testUtils/pages/ProcessInstance/v2';
import {render} from 'modules/testing-library';
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';

const getWrapper = (
  initialPath: string,
): React.FC<{children?: React.ReactNode}> => {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    return (
      <ProcessDefinitionKeyContext.Provider value="123">
        <QueryClientProvider client={getMockQueryClient()}>
          <MemoryRouter initialEntries={[initialPath]}>
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
  return Wrapper;
};

const renderPopover = (searchParams?: {
  elementId?: string;
  elementInstanceKey?: string;
}) => {
  const svgElement = document.createElementNS(
    'http://www.w3.org/2000/svg',
    'svg',
  );

  let initialPath = Paths.processInstance('2251799813685591');
  if (searchParams) {
    const params = new URLSearchParams(searchParams);
    initialPath += `?${params.toString()}`;
  }

  return render(<MetadataPopover selectedFlowNodeRef={svgElement} />, {
    wrapper: getWrapper(initialPath),
  });
};

const {
  metadataPopover: {labels},
} = new ProcessInstance();

export {labels, renderPopover};
