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
  mockProcessWithInputOutputMappingsXML,
  mockProcessXML,
} from 'modules/testUtils';
import {processesStore} from 'modules/stores/processes/processes.migration';
import {TargetDiagram} from '../TargetDiagram';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {act} from 'react-dom/test-utils';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {processXmlStore} from 'modules/stores/processXml/processXml.migration.target';

type Props = {
  children?: React.ReactNode;
};

const Wrapper = ({children}: Props) => {
  useEffect(() => {
    return () => {
      processesStore.reset();
      processInstanceMigrationStore.reset();
      processXmlStore.reset();
    };
  });

  return <>{children}</>;
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

    processInstanceMigrationStore.setCurrentStep('elementMapping');

    await processesStore.fetchProcesses();
    const {user} = render(<TargetDiagram />, {wrapper: Wrapper});

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
    act(() => processInstanceMigrationStore.setCurrentStep('summary'));

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

    act(() => processInstanceMigrationStore.setCurrentStep('elementMapping'));

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

    processInstanceMigrationStore.setCurrentStep('elementMapping');

    await processesStore.fetchProcesses();

    const {user} = render(<TargetDiagram />, {wrapper: Wrapper});

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

    processInstanceMigrationStore.setCurrentStep('elementMapping');

    await processesStore.fetchProcesses();

    const {user} = render(<TargetDiagram />, {wrapper: Wrapper});

    await user.click(screen.getByRole('combobox', {name: 'Target Process'}));
    await user.click(screen.getByRole('option', {name: 'New demo process'}));

    expect(
      await screen.findByText('Data could not be fetched'),
    ).toBeInTheDocument();
  });
});
