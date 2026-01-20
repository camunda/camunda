/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, type Screen} from 'modules/testing-library';
import {PROCESS_INSTANCE_ID} from 'modules/mocks/metadata';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {createInstance} from 'modules/testUtils';
import {createRef, useEffect, useState} from 'react';
import {MemoryRouter, Route, Routes, useSearchParams} from 'react-router-dom';
import {ModificationDropdown} from '../';
import {modificationsStore} from 'modules/stores/modifications';
import {Paths} from 'modules/Routes';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {QueryClientProvider} from '@tanstack/react-query';
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import type {UserEvent} from '@testing-library/user-event';

const selectElementId = async ({
  screen,
  user,
  elementId,
}: {
  screen: Screen;
  user: UserEvent;
  elementId: string;
}) => {
  await user.type(screen.getByRole('textbox', {name: 'element-id'}), elementId);
  await user.click(screen.getByRole('button', {name: 'Submit'}));
};

const ElementSelector: React.FC = () => {
  const [_, setSearchParams] = useSearchParams();
  const [value, setValue] = useState('');

  return (
    <div>
      <form
        onSubmit={(event) => {
          event.preventDefault();
          setSearchParams(`?elementId=${value}`);
        }}
      >
        <label htmlFor="element-id">element-id</label>
        <input
          type="text"
          id="element-id"
          value={value}
          onChange={(e) => setValue(e.target.value)}
        />
        <button type="submit">Submit</button>
      </form>
    </div>
  );
};

const getWrapper = (
  initialEntries: React.ComponentProps<typeof MemoryRouter>['initialEntries'],
) => {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    useEffect(() => {
      initializeStores();

      return resetStores;
    }, []);

    return (
      <ProcessDefinitionKeyContext.Provider value="123">
        <QueryClientProvider client={getMockQueryClient()}>
          <MemoryRouter initialEntries={initialEntries}>
            <Routes>
              <Route path={Paths.processInstance()} element={children} />
            </Routes>
            <ElementSelector />
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

const initializeStores = () => {
  processInstanceDetailsStore.setProcessInstance(
    createInstance({
      id: PROCESS_INSTANCE_ID,
      state: 'ACTIVE',
      processId: 'processId',
    }),
  );
};

const resetStores = () => {
  processInstanceDetailsStore.reset();
  modificationsStore.reset();
};

export {renderPopover, selectElementId};
