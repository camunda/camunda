/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {MemoryRouter} from 'react-router-dom';
import {groupedProcessesMock} from 'modules/testUtils';
import {processStatisticsBatchModificationStore} from 'modules/stores/processStatistics/processStatistics.batchModification';
import {batchModificationStore} from 'modules/stores/batchModification';
import {mockFetchGroupedProcesses} from 'modules/mocks/api/processes/fetchGroupedProcesses';
import {useEffect} from 'react';
import {Paths} from 'modules/Routes';
import {BatchModificationNotification} from '.';
import {mockFetchProcessInstancesStatistics} from 'modules/mocks/api/processInstances/fetchProcessInstancesStatistics';
import {mockProcessStatisticsWithActiveAndIncidents} from 'modules/mocks/mockProcessStatistics';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';

jest.mock('modules/utils/bpmn');

const notificationText1 =
  'Please select where you want to move the selected instances on the diagram.';

const notificationText2 =
  'Modification scheduled: Move 4 instances from “userTask” to “endEvent”. Press “Apply Modification” button to confirm.';

function getWrapper(initialPath: string = Paths.dashboard()) {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    useEffect(() => {
      return () => {
        processStatisticsBatchModificationStore.reset();
        batchModificationStore.reset();
      };
    }, []);

    return (
      <QueryClientProvider client={getMockQueryClient()}>
        <MemoryRouter initialEntries={[initialPath]}>
          {children}
          <button
            onClick={() => {
              processStatisticsBatchModificationStore.fetchProcessStatistics();
            }}
          >
            Fetch modification statistics
          </button>
        </MemoryRouter>
      </QueryClientProvider>
    );
  };

  return Wrapper;
}

describe('DiagramPanel', () => {
  beforeEach(() => {
    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);
    mockFetchProcessInstancesStatistics().withSuccess(
      mockProcessStatisticsWithActiveAndIncidents,
    );
  });

  it('should initially render batch modification notification', async () => {
    const {user} = render(
      <BatchModificationNotification
        sourceFlowNodeId="userTask"
        targetFlowNodeId="endEvent"
      />,
      {
        wrapper: getWrapper(),
      },
    );

    await user.click(
      screen.getByRole('button', {name: /Fetch modification statistics/i}),
    );

    expect(await screen.findByText(notificationText2)).toBeInTheDocument();
    expect(screen.queryByText(notificationText1)).not.toBeInTheDocument();
  });

  it('should render Undo button if target is selected', async () => {
    const undoMock = jest.fn();

    const {user} = render(
      <BatchModificationNotification
        sourceFlowNodeId="userTask"
        targetFlowNodeId="endEvent"
        onUndoClick={undoMock}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    await user.click(
      screen.getByRole('button', {name: /Fetch modification statistics/i}),
    );

    expect(await screen.findByText(notificationText2)).toBeInTheDocument();
    expect(screen.queryByText(notificationText1)).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: /undo/i}));

    expect(undoMock).toHaveBeenCalled();
  });

  it('should not render Undo button if no target is selected', async () => {
    render(<BatchModificationNotification sourceFlowNodeId="userTask" />, {
      wrapper: getWrapper(),
    });

    expect(screen.getByText(notificationText1)).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /undo/i}),
    ).not.toBeInTheDocument();
  });
});
