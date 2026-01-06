/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {act, render, screen} from 'modules/testing-library';
import {Bar} from '../index';
import {mockStartNode, mockStartEventBusinessObject} from '../index.setup';
import {flowNodeTimeStampStore} from 'modules/stores/flowNodeTimeStamp';
import {MOCK_TIMESTAMP} from 'modules/utils/date/__mocks__/formatDate';
import {useEffect} from 'react';
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {Paths} from 'modules/Routes';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockProcessInstance} from 'modules/mocks/api/v2/mocks/processInstance';

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  useEffect(() => {
    return () => {
      flowNodeTimeStampStore.reset();
    };
  }, []);

  return (
    <ProcessDefinitionKeyContext.Provider value="123">
      <QueryClientProvider client={getMockQueryClient()}>
        <MemoryRouter initialEntries={[Paths.processInstance('1')]}>
          <Routes>
            <Route path={Paths.processInstance()} element={children} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    </ProcessDefinitionKeyContext.Provider>
  );
};

describe('<Bar />', () => {
  it('should show the node name and an icon based on node state', () => {
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchProcessDefinitionXml().withSuccess('');

    render(
      <Bar
        flowNodeInstance={mockStartNode}
        nodeName={mockStartEventBusinessObject.name}
      />,
      {
        wrapper: Wrapper,
      },
    );

    expect(screen.getByTestId('ACTIVE-icon')).toBeInTheDocument();
    expect(
      screen.getByText(mockStartEventBusinessObject.name),
    ).toBeInTheDocument();
  });

  it('should toggle the timestamp', async () => {
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchProcessDefinitionXml().withSuccess('');

    render(
      <Bar
        flowNodeInstance={{...mockStartNode, endDate: MOCK_TIMESTAMP}}
        nodeName={mockStartEventBusinessObject.name}
        isTimestampLabelVisible
      />,
      {
        wrapper: Wrapper,
      },
    );

    expect(screen.queryByText(MOCK_TIMESTAMP)).not.toBeInTheDocument();

    act(() => {
      flowNodeTimeStampStore.toggleTimeStampVisibility();
    });

    expect(screen.getByText(MOCK_TIMESTAMP)).toBeInTheDocument();
  });

  it('should show latest successful migration date', async () => {
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchProcessDefinitionXml().withSuccess('');

    render(
      <Bar
        flowNodeInstance={mockStartNode}
        nodeName={mockStartEventBusinessObject.name}
        isRoot
        latestMigrationDate={MOCK_TIMESTAMP}
      />,
      {
        wrapper: Wrapper,
      },
    );

    expect(
      screen.getByText(`Migrated 2018-12-12 00:00:00`),
    ).toBeInTheDocument();
  });

  it('should not show latest successful migration date for non-root', async () => {
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchProcessDefinitionXml().withSuccess('');

    render(
      <Bar
        flowNodeInstance={mockStartNode}
        nodeName={mockStartEventBusinessObject.name}
        latestMigrationDate={MOCK_TIMESTAMP}
      />,
      {
        wrapper: Wrapper,
      },
    );

    expect(
      screen.queryByText(`Migrated 2018-12-12 00:00:00`),
    ).not.toBeInTheDocument();
  });
});
