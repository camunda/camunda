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
import {MetadataPopover} from './indexV2';
import {ProcessInstance} from 'modules/testUtils/pages/ProcessInstance/v2';
import {render} from 'modules/testing-library';
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';

const Wrapper: React.FC<{
  children?: React.ReactNode;
  initialSearchParams?: Record<string, string>;
}> = ({children, initialSearchParams}) => {
  const searchParams = new URLSearchParams(initialSearchParams);
  const path =
    Paths.processInstance('2251799813685591') +
    (searchParams.toString() ? `?${searchParams.toString()}` : '');

  return (
    <ProcessDefinitionKeyContext.Provider value="123">
      <QueryClientProvider client={getMockQueryClient()}>
        <MemoryRouter initialEntries={[path]}>
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

const renderPopover = (initialSearchParams?: Record<string, string>) => {
  const svgElement = document.createElementNS(
    'http://www.w3.org/2000/svg',
    'svg',
  );

  const WrapperWithParams: React.FC<{children?: React.ReactNode}> = ({
    children,
  }) => <Wrapper initialSearchParams={initialSearchParams}>{children}</Wrapper>;

  return render(<MetadataPopover selectedFlowNodeRef={svgElement} />, {
    wrapper: WrapperWithParams,
  });
};

const {
  metadataPopover: {labels},
} = new ProcessInstance();

export {labels, renderPopover};
