/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {MemoryRouter} from 'react-router-dom';
import {Paths} from 'modules/Routes';
import {BatchModificationNotification} from '.';
import {mockFetchProcessInstancesStatistics} from 'modules/mocks/api/v2/processInstances/fetchProcessInstancesStatistics';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {QueryClientProvider} from '@tanstack/react-query';
import * as filterModule from 'modules/hooks/useProcessInstancesFilters';
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockProcessXML} from 'modules/testUtils';

jest.mock('modules/hooks/useProcessInstancesFilters');
jest.mock('modules/stores/processes/processes.list', () => ({
  processesStore: {
    getProcessIdByLocation: () => '123',
  },
}));

const notificationText1 =
  'Please select where you want to move the selected instances on the diagram.';

const notificationText2 =
  'Modification scheduled: Move 4 instances from “userTask” to “endEvent”. Press “Apply Modification” button to confirm.';

function getWrapper(initialPath: string = Paths.dashboard()) {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    return (
      <MemoryRouter initialEntries={[initialPath]}>
        <ProcessDefinitionKeyContext.Provider value={'123'}>
          <QueryClientProvider client={getMockQueryClient()}>
            {children}
          </QueryClientProvider>
        </ProcessDefinitionKeyContext.Provider>
      </MemoryRouter>
    );
  };

  return Wrapper;
}

describe('BatchModificationNotification', () => {
  const originalWindow = {...window};
  const locationSpy = jest.spyOn(window, 'location', 'get');
  const queryString = '?process=bigVarProcess&version=1';
  locationSpy.mockImplementation(() => ({
    ...originalWindow.location,
    search: queryString,
  }));

  beforeEach(() => {
    jest.spyOn(filterModule, 'useProcessInstanceFilters').mockReturnValue({});
    mockFetchProcessInstancesStatistics().withSuccess({
      items: [],
    });
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('should render batch modification notification with instance count', async () => {
    mockFetchProcessInstancesStatistics().withSuccess({
      items: [
        {
          elementId: 'userTask',
          active: 4,
          canceled: 0,
          incidents: 0,
          completed: 0,
        },
      ],
    });

    render(
      <BatchModificationNotification
        sourceFlowNodeId="userTask"
        targetFlowNodeId="endEvent"
      />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(await screen.findByText(notificationText2)).toBeInTheDocument();
    expect(screen.queryByText(notificationText1)).not.toBeInTheDocument();
  });

  it('should render Undo button if target is selected', async () => {
    const undoMock = jest.fn();

    mockFetchProcessInstancesStatistics().withSuccess({
      items: [
        {
          elementId: 'userTask',
          active: 4,
          canceled: 0,
          incidents: 0,
          completed: 0,
        },
      ],
    });

    render(
      <BatchModificationNotification
        sourceFlowNodeId="userTask"
        targetFlowNodeId="endEvent"
        onUndoClick={undoMock}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(await screen.findByText(notificationText2)).toBeInTheDocument();
    expect(screen.queryByText(notificationText1)).not.toBeInTheDocument();

    const undoButton = screen.getByRole('button', {name: /undo/i});
    expect(undoButton).toBeInTheDocument();

    undoButton.click();
    expect(undoMock).toHaveBeenCalled();
  });

  it('should not render Undo button if no target is selected', async () => {
    mockFetchProcessInstancesStatistics().withSuccess({
      items: [
        {
          elementId: 'userTask',
          active: 4,
          canceled: 0,
          incidents: 0,
          completed: 0,
        },
      ],
    });

    render(<BatchModificationNotification sourceFlowNodeId="userTask" />, {
      wrapper: getWrapper(),
    });

    expect(screen.getByText(notificationText1)).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /undo/i}),
    ).not.toBeInTheDocument();
  });
});
