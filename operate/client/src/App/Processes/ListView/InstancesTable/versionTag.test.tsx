/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {InstancesTable} from '.';
import {unstable_HistoryRouter as HistoryRouter} from 'react-router-dom';
import {useEffect} from 'react';
import {createMemoryHistory} from 'history';
import {processInstancesStore} from 'modules/stores/processInstances';
import {processesStore} from 'modules/stores/processes/processes.list';
import {mockFetchGroupedProcesses} from 'modules/mocks/api/processes/fetchGroupedProcesses';
import {createInstance, groupedProcessesMock} from 'modules/testUtils';
import {mockFetchProcessInstances} from 'modules/mocks/api/processInstances/fetchProcessInstances';

const createProcessInstance = ({
  bpmnProcessId,
  version,
}: {
  bpmnProcessId: string;
  version: number;
}) => {
  const processDefinitions = groupedProcessesMock.find((process) => {
    return process.bpmnProcessId === bpmnProcessId;
  })?.processes;

  const processDefinition = processDefinitions?.find((processDefinition) => {
    return processDefinition.version === version;
  });

  return createInstance({
    bpmnProcessId: processDefinition?.bpmnProcessId,
    processVersion: processDefinition?.version,
    processId: processDefinition?.id,
  });
};

function getWrapper() {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    useEffect(() => {
      processesStore.fetchProcesses();
      processInstancesStore.fetchProcessInstancesFromFilters();

      return () => {
        processesStore.reset();
        processInstancesStore.reset();
      };
    });

    return (
      <HistoryRouter
        // @ts-expect-error - history v5.3.0 lacks encodeLocation required by react-router-dom
        history={createMemoryHistory()}
      >
        {children}
      </HistoryRouter>
    );
  };

  return Wrapper;
}

describe('<InstancesTable /> - version tag', () => {
  it('should show version tag column (header + content)', async () => {
    const processInstance = createProcessInstance({
      bpmnProcessId: 'bigVarProcess',
      version: 1,
    });

    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);
    mockFetchProcessInstances().withSuccess({
      totalCount: 1,
      processInstances: [processInstance],
    });

    render(<InstancesTable />, {
      wrapper: getWrapper(),
    });

    // wait for process instance to be rendered
    expect(await screen.findByTestId('cell-processName')).toHaveTextContent(
      processInstance.processName,
    );

    /**
     * Expect that the version tag is rendered, because bigVarProcess version 1
     * has a related version tag.
     */
    expect(screen.getByTestId('cell-versionTag')).toHaveTextContent(
      'MyVersionTag',
    );
    expect(
      screen.getByRole('columnheader', {name: /Version Tag/i}),
    ).toBeInTheDocument();
  });

  it('should show version tag column (only header)', async () => {
    const demoProcessInstance = createProcessInstance({
      bpmnProcessId: 'demoProcess',
      version: 1,
    });
    const bigVarProcessInstance = createProcessInstance({
      bpmnProcessId: 'bigVarProcess',
      version: 1,
    });

    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);
    mockFetchProcessInstances().withSuccess({
      totalCount: 2,
      processInstances: [demoProcessInstance, bigVarProcessInstance],
    });

    render(<InstancesTable />, {
      wrapper: getWrapper(),
    });

    // wait for process instance to be rendered
    expect(await screen.findAllByTestId('cell-processName')).toHaveLength(2);

    const versionTagCells = screen.getAllByTestId('cell-versionTag');

    /**
     * Expect that no version tag is rendered for demoProcess version 1
     */
    expect(versionTagCells[0]).toHaveTextContent('--');

    /**
     * Expect that version tag is rendered for bigVarProcess version 1
     */
    expect(versionTagCells[1]).toHaveTextContent('MyVersionTag');

    /**
     * Expect that the version tag column header is rendered, because there is at least
     * one process definition with a version tag (bigVarProcess, version 1).
     */
    expect(
      screen.getByRole('columnheader', {name: /Version Tag/i}),
    ).toBeInTheDocument();
  });

  it('should not show version tag column when there is no process with a version tag', async () => {
    const processInstance = createProcessInstance({
      bpmnProcessId: 'demoProcess',
      version: 1,
    });

    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);
    mockFetchProcessInstances().withSuccess({
      totalCount: 1,
      processInstances: [processInstance],
    });

    render(<InstancesTable />, {
      wrapper: getWrapper(),
    });

    // wait for process instance to be rendered
    expect(await screen.findByTestId('cell-processName')).toHaveTextContent(
      processInstance.processName,
    );

    /**
     * Expect that no version tag is rendered, because demoProcess version 1
     * has no related version tag.
     */
    expect(screen.queryByTestId('cell-versionTag')).not.toBeInTheDocument();

    /**
     * Expect that no version tag column header is rendered, because there no
     * other process definition with a version tag.
     */
    expect(
      screen.queryByRole('columnheader', {name: /Version Tag/i}),
    ).not.toBeInTheDocument();
  });
});
