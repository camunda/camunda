/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen} from 'modules/testing-library';
import {useEffect} from 'react';
import {mockFetchGroupedProcesses} from 'modules/mocks/api/processes/fetchGroupedProcesses';
import {
  groupedProcessesMock,
  mockProcessStatistics,
  mockProcessXML,
} from 'modules/testUtils';
import {processesStore} from 'modules/stores/processes/processes.migration';
import {SourceDiagram} from '../SourceDiagram';
import {processXmlStore} from 'modules/stores/processXml/processXml.migration.source';
import {processStatisticsStore} from 'modules/stores/processStatistics/processStatistics.migration.source';
import {mockFetchProcessInstancesStatistics} from 'modules/mocks/api/processInstances/fetchProcessInstancesStatistics';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';

type Props = {
  children?: React.ReactNode;
};

const Wrapper = ({children}: Props) => {
  useEffect(() => {
    return () => {
      processesStore.reset();
      processInstanceMigrationStore.reset();
      processStatisticsStore.reset();
    };
  });

  return (
    <>
      {children}{' '}
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
    </>
  );
};

describe('Source Diagram', () => {
  it('should render process name and version', async () => {
    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);

    const originalWindow = {...window};
    const locationSpy = jest.spyOn(window, 'location', 'get');

    locationSpy.mockImplementation(() => ({
      ...originalWindow.location,
      search: '?active=true&incidents=true&process=demoProcess&version=3',
    }));

    processesStore.fetchProcesses();
    render(<SourceDiagram />, {wrapper: Wrapper});

    expect(await screen.findByText('New demo process')).toBeInTheDocument();
    expect(screen.getByText('Source')).toBeInTheDocument();
    expect(screen.getByText('3')).toBeInTheDocument();
    expect(screen.getByText('Version')).toBeInTheDocument();

    locationSpy.mockRestore();
  });

  it('should render xml', async () => {
    processXmlStore.setProcessXml(mockProcessXML);

    render(<SourceDiagram />, {wrapper: Wrapper});

    expect(await screen.findByTestId('diagram')).toBeInTheDocument();
    expect(screen.getByRole('button', {name: /reset diagram zoom/i}));
    expect(screen.getByRole('button', {name: /zoom in diagram/i}));
    expect(screen.getByRole('button', {name: /zoom out diagram/i}));
  });

  it('should render statistics overlays', async () => {
    processXmlStore.setProcessXml(mockProcessXML);
    mockFetchProcessInstancesStatistics().withSuccess(mockProcessStatistics);
    processStatisticsStore.fetchProcessStatistics();

    const {user} = render(<SourceDiagram />, {wrapper: Wrapper});

    await user.click(screen.getByRole('button', {name: /summary/i}));

    expect(await screen.findByText(/diagram mock/i)).toBeInTheDocument();
    expect(await screen.findByTestId('state-overlay')).toBeInTheDocument();
    expect(screen.getByTestId('state-overlay')).toHaveTextContent('1');

    await user.click(screen.getByRole('button', {name: /element mapping/i}));

    expect(await screen.findByText(/diagram mock/i)).toBeInTheDocument();
    expect(screen.queryByTestId('state-overlay')).not.toBeInTheDocument();
  });
});
