/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render} from 'modules/testing-library';
import {createRef} from 'react';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {ModificationDropdown} from '../index';
import {Paths} from 'modules/Routes';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {QueryClientProvider} from '@tanstack/react-query';
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';

const getWrapper = (
  initialEntries: React.ComponentProps<typeof MemoryRouter>['initialEntries'],
) => {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    return (
      <ProcessDefinitionKeyContext.Provider value="123">
        <QueryClientProvider client={getMockQueryClient()}>
          <MemoryRouter initialEntries={initialEntries}>
            <Routes>
              <Route path={Paths.processInstance()} element={children} />
            </Routes>
          </MemoryRouter>
        </QueryClientProvider>
      </ProcessDefinitionKeyContext.Provider>
    );
  };
  return Wrapper;
};

const renderPopover = (
  initialEntries: React.ComponentProps<
    typeof MemoryRouter
  >['initialEntries'] = [Paths.processInstance('1')],
) => {
  const svgElement = document.createElementNS(
    'http://www.w3.org/2000/svg',
    'svg',
  );
  const ref = createRef<HTMLDivElement>();

  return render(
    <ModificationDropdown
      selectedFlowNodeRef={svgElement}
      diagramCanvasRef={ref}
    />,
    {
      wrapper: getWrapper(initialEntries),
    },
  );
};

export {renderPopover};
