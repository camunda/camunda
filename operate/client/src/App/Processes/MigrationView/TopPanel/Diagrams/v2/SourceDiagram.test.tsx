/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {mockProcessStatisticsV2, mockProcessXML} from 'modules/testUtils';
import {processesStore} from 'modules/stores/processes/processes.migration';
import {SourceDiagram} from './SourceDiagram';
import {mockFetchProcessInstancesStatistics} from 'modules/mocks/api/v2/processInstances/fetchProcessInstancesStatistics';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {useEffect} from 'react';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelection';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';

jest.mock('modules/hooks/useProcessInstancesFilters');
jest.mock('modules/stores/processes/processes.migration');

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  useEffect(() => {
    return () => {
      processInstanceMigrationStore.reset();
      processInstancesSelectionStore.reset();
    };
  });

  return (
    <QueryClientProvider client={getMockQueryClient()}>
      {children}
      <button
        onClick={() => processInstanceMigrationStore.setCurrentStep('summary')}
      >
        Summary
      </button>
      <button
        onClick={() =>
          processInstanceMigrationStore.setCurrentStep('elementMapping')
        }
      >
        Element Mapping
      </button>
      <button
        onClick={() => {
          processInstanceMigrationStore.updateFlowNodeMapping({
            sourceId: 'ServiceTask_0kt6c5i',
            targetId: 'ServiceTask_0kt6c5i',
          });
        }}
      >
        map elements
      </button>
    </QueryClientProvider>
  );
};

describe('Source Diagram', () => {
  beforeEach(() => {
    // @ts-expect-error
    processesStore.getSelectedProcessDetails.mockReturnValue({
      processName: 'New demo process',
      version: '3',
      bpmnProcessId: 'demoProcess',
    });
  });

  it('should render process name and version', async () => {
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);

    const originalWindow = {...window};
    const locationSpy = jest.spyOn(window, 'location', 'get');

    locationSpy.mockImplementation(() => ({
      ...originalWindow.location,
      search: '?active=true&incidents=true&process=demoProcess&version=3',
    }));

    render(<SourceDiagram />, {wrapper: Wrapper});

    expect(await screen.findByText('New demo process')).toBeInTheDocument();
    expect(screen.getByText('Source')).toBeInTheDocument();
    expect(screen.getByText('3')).toBeInTheDocument();
    expect(screen.getByText('Version')).toBeInTheDocument();

    locationSpy.mockRestore();
  });

  it('should render xml', async () => {
    processInstanceMigrationStore.setSourceProcessDefinitionKey('123');
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);

    render(<SourceDiagram />, {wrapper: Wrapper});

    expect(await screen.findByTestId('diagram')).toBeInTheDocument();
    expect(screen.getByRole('button', {name: /reset diagram zoom/i}));
    expect(screen.getByRole('button', {name: /zoom in diagram/i}));
    expect(screen.getByRole('button', {name: /zoom out diagram/i}));
  });

  it('should render statistics overlays', async () => {
    processInstanceMigrationStore.setSourceProcessDefinitionKey('123');
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);
    processInstancesSelectionStore.setselectedProcessInstanceIds([
      'processInstance1',
    ]);
    mockFetchProcessInstancesStatistics().withSuccess(mockProcessStatisticsV2);

    const {user} = render(<SourceDiagram />, {wrapper: Wrapper});

    await user.click(screen.getByRole('button', {name: /summary/i}));

    expect(await screen.findByText(/diagram mock/i)).toBeInTheDocument();
    expect(
      await screen.findByTestId('state-overlay-active'),
    ).toBeInTheDocument();
    expect(screen.getByTestId('state-overlay-active')).toHaveTextContent('1');

    await user.click(screen.getByRole('button', {name: /element mapping/i}));

    expect(await screen.findByText(/diagram mock/i)).toBeInTheDocument();
    expect(screen.queryByTestId('state-overlay')).not.toBeInTheDocument();
  });
});
