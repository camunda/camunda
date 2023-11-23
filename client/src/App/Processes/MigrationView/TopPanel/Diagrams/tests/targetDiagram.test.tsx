/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  render,
  screen,
  waitForElementToBeRemoved,
} from 'modules/testing-library';
import {useEffect} from 'react';
import {mockFetchGroupedProcesses} from 'modules/mocks/api/processes/fetchGroupedProcesses';
import {
  groupedProcessesMock,
  mockProcessStatistics,
  mockProcessWithInputOutputMappingsXML,
  mockProcessXML,
} from 'modules/testUtils';
import {processesStore} from 'modules/stores/processes/processes.migration';
import {TargetDiagram} from '../TargetDiagram';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {processXmlStore} from 'modules/stores/processXml/processXml.migration.target';
import {mockFetchProcessInstancesStatistics} from 'modules/mocks/api/processInstances/fetchProcessInstancesStatistics';
import {processStatisticsStore} from 'modules/stores/processStatistics/processStatistics.migration.source';

type Props = {
  children?: React.ReactNode;
};

const Wrapper = ({children}: Props) => {
  useEffect(() => {
    return () => {
      processesStore.reset();
      processInstanceMigrationStore.reset();
      processXmlStore.reset();
      processStatisticsStore.reset();
    };
  });

  return (
    <>
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
    </>
  );
};

describe('Target Diagram', () => {
  it('should display initial state in the diagram header and diagram panel', async () => {
    processInstanceMigrationStore.setCurrentStep('elementMapping');

    render(<TargetDiagram />, {wrapper: Wrapper});

    expect(screen.getByText('Target')).toBeInTheDocument();
    expect(screen.getByText('Version')).toBeInTheDocument();
    expect(
      screen.getByRole('combobox', {
        name: /target process/i,
      }),
    ).toHaveTextContent(/Select target process/i);
    expect(
      screen.getByRole('combobox', {
        name: /target version/i,
      }),
    ).toBeDisabled();
    expect(
      screen.getByRole('combobox', {
        name: /target version/i,
      }),
    ).toHaveTextContent('-');

    expect(
      screen.getByText('Select a target process and version'),
    ).toBeInTheDocument();
  });

  it('should render process and version components according to the step number', async () => {
    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);
    mockFetchProcessXML().withSuccess(mockProcessXML);

    await processesStore.fetchProcesses();
    const {user} = render(<TargetDiagram />, {wrapper: Wrapper});

    await user.click(screen.getByRole('button', {name: /element mapping/i}));
    await user.click(screen.getByRole('combobox', {name: 'Target Process'}));
    await user.click(screen.getByRole('option', {name: 'New demo process'}));

    expect(
      screen.getByRole('combobox', {
        name: /target process/i,
      }),
    ).toHaveTextContent(/New demo process/i);
    expect(
      screen.getByRole('combobox', {
        name: /target version/i,
      }),
    ).toHaveTextContent('3');
    expect(
      screen.getByRole('combobox', {
        name: /target version/i,
      }),
    ).toBeEnabled();

    expect(await screen.findByTestId('diagram')).toBeInTheDocument();
    await user.click(screen.getByRole('button', {name: /summary/i}));

    expect(
      screen.queryByRole('combobox', {
        name: /target process/i,
      }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('combobox', {
        name: /target version/i,
      }),
    ).not.toBeInTheDocument();

    expect(screen.getByText('New demo process')).toBeInTheDocument();
    expect(screen.getByText('3')).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: /element mapping/i}));

    expect(
      screen.getByRole('combobox', {
        name: /target process/i,
      }),
    ).toHaveTextContent(/New demo process/i);
    expect(
      screen.getByRole('combobox', {
        name: /target version/i,
      }),
    ).toHaveTextContent('3');
  });

  it('should render diagram on selection and re-render on version change', async () => {
    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);
    mockFetchProcessXML().withSuccess(mockProcessXML);

    await processesStore.fetchProcesses();

    const {user} = render(<TargetDiagram />, {wrapper: Wrapper});

    await user.click(screen.getByRole('button', {name: /element mapping/i}));
    await user.click(screen.getByRole('combobox', {name: 'Target Process'}));
    await user.click(screen.getByRole('option', {name: 'New demo process'}));

    expect(await screen.findByTestId('diagram')).toBeInTheDocument();
    expect(screen.getByRole('button', {name: /reset diagram zoom/i}));
    expect(screen.getByRole('button', {name: /zoom in diagram/i}));
    expect(screen.getByRole('button', {name: /zoom out diagram/i}));

    mockFetchProcessXML().withDelay(mockProcessWithInputOutputMappingsXML);

    await user.click(screen.getByRole('combobox', {name: 'Target Version'}));
    await user.click(screen.getByRole('option', {name: '2'}));

    expect(await screen.findByTestId('diagram-spinner')).toBeInTheDocument();

    await waitForElementToBeRemoved(() =>
      screen.getByTestId('diagram-spinner'),
    );
  });

  it('should display error message on selection if diagram could not be fetched', async () => {
    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);
    mockFetchProcessXML().withServerError();
    await processesStore.fetchProcesses();

    const {user} = render(<TargetDiagram />, {wrapper: Wrapper});

    await user.click(screen.getByRole('button', {name: /element mapping/i}));
    await user.click(screen.getByRole('combobox', {name: 'Target Process'}));
    await user.click(screen.getByRole('option', {name: 'New demo process'}));

    expect(
      await screen.findByText('Data could not be fetched'),
    ).toBeInTheDocument();
  });

  it('should render flow node overlays', async () => {
    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);
    mockFetchProcessXML().withSuccess(mockProcessXML);
    mockFetchProcessInstancesStatistics().withSuccess(mockProcessStatistics);
    await processesStore.fetchProcesses();
    await processStatisticsStore.fetchProcessStatistics();

    const {user} = render(<TargetDiagram />, {wrapper: Wrapper});

    await user.click(screen.getByRole('button', {name: /element mapping/i}));
    await user.click(screen.getByRole('combobox', {name: 'Target Process'}));
    await user.click(screen.getByRole('option', {name: 'New demo process'}));
    await user.click(screen.getByRole('button', {name: /map elements/i}));

    expect(await screen.findByText(/diagram mock/i)).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: /summary/i}));

    expect(
      await screen.findByTestId('modifications-overlay'),
    ).toBeInTheDocument();
    expect(screen.getByTestId('modifications-overlay')).toHaveTextContent('1');

    await user.click(screen.getByRole('button', {name: /element mapping/i}));

    expect(await screen.findByText(/diagram mock/i)).toBeInTheDocument();
    expect(
      screen.queryByTestId('modifications-overlay'),
    ).not.toBeInTheDocument();
  });
});
