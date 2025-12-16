/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {DiagramHeader} from '.';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {mockSearchProcessInstances} from 'modules/mocks/api/v2/processInstances/searchProcessInstances';
import {processesStore} from 'modules/stores/processes/processes.list';
import {mockProcessDefinitions} from 'modules/testUtils';
import {mockSearchProcessDefinitions} from 'modules/mocks/api/v2/processDefinitions/searchProcessDefinitions';

function getWrapper() {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    return (
      <QueryClientProvider client={new QueryClient()}>
        {children}
      </QueryClientProvider>
    );
  };

  return Wrapper;
}

describe('DiagramHeader', () => {
  beforeEach(() => {
    mockSearchProcessDefinitions().withSuccess(mockProcessDefinitions);
    mockSearchProcessInstances().withSuccess({
      items: [],
      page: {totalItems: 0},
    });

    processesStore.fetchProcesses();
  });

  it('should render header with full data', async () => {
    render(
      <DiagramHeader
        processDetails={{
          processName: 'My Process',
          bpmnProcessId: 'MyProcess',
          version: '1',
          versionTag: 'MyVersionTag',
        }}
        processDefinitionKey="123"
      />,
      {wrapper: getWrapper()},
    );

    expect(screen.getByText(/^process name$/i)).toBeInTheDocument();
    expect(screen.getByText(/^my process$/i)).toBeInTheDocument();

    expect(screen.getByText(/^process id$/i)).toBeInTheDocument();
    expect(screen.getByText(/^MyProcess$/i)).toBeInTheDocument();

    expect(screen.getByText(/^version tag$/i)).toBeInTheDocument();
    expect(screen.getByText(/^MyVersionTag$/i)).toBeInTheDocument();

    expect(
      await screen.findByRole('button', {name: /delete process definition/i}),
    ).toBeInTheDocument();
  });

  it('should render header without version tag', async () => {
    render(
      <DiagramHeader
        processDetails={{
          processName: 'My Process',
          bpmnProcessId: 'MyProcess',
          version: '1',
        }}
        processDefinitionKey="123"
      />,
      {wrapper: getWrapper()},
    );

    expect(screen.getByText(/^process name$/i)).toBeInTheDocument();
    expect(screen.getByText(/^my process$/i)).toBeInTheDocument();

    expect(screen.getByText(/^process id$/i)).toBeInTheDocument();
    expect(screen.getByText(/^MyProcess$/i)).toBeInTheDocument();

    expect(screen.queryByText(/^version tag$/i)).not.toBeInTheDocument();

    expect(
      await screen.findByRole('button', {name: /delete process definition/i}),
    ).toBeInTheDocument();
  });

  it('should render header without data', async () => {
    render(
      <DiagramHeader
        processDetails={{
          processName: 'My Process',
        }}
        processDefinitionKey=""
      />,
      {wrapper: getWrapper()},
    );

    expect(screen.queryByText(/^process name$/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/^process id$/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/^version tag$/i)).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /delete process definition/i}),
    ).not.toBeInTheDocument();
  });

  it('should disable delete button when running instances count is greater than 0', async () => {
    mockSearchProcessInstances().withSuccess({
      items: [],
      page: {totalItems: 5},
    });

    render(
      <DiagramHeader
        processDetails={{
          processName: 'My Process',
          bpmnProcessId: 'MyProcess',
          version: '1',
        }}
        processDefinitionKey="123"
      />,
      {wrapper: getWrapper()},
    );

    const deleteButton = await screen.findByRole('button', {
      name: /only process definitions without running instances can be deleted/i,
    });
    expect(deleteButton).toBeInTheDocument();
    expect(deleteButton).toBeDisabled();
    expect(deleteButton).toHaveAttribute(
      'title',
      'Only process definitions without running instances can be deleted.',
    );
  });

  it('should enable delete button when running instances count is 0', async () => {
    render(
      <DiagramHeader
        processDetails={{
          processName: 'My Process',
          bpmnProcessId: 'MyProcess',
          version: '1',
        }}
        processDefinitionKey="123"
      />,
      {wrapper: getWrapper()},
    );

    const deleteButton = await screen.findByRole('button', {
      name: /delete process definition/i,
    });
    expect(deleteButton).toBeInTheDocument();
    expect(deleteButton).not.toBeDisabled();
    expect(deleteButton).toHaveAttribute(
      'title',
      'Delete Process Definition "My Process - Version 1"',
    );
  });
});
