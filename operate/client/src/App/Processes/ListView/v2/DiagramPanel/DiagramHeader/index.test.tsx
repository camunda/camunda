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
import {searchResult} from 'modules/testUtils';
import type {ProcessDefinitionSelection} from 'modules/hooks/processDefinitions';

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  return (
    <QueryClientProvider client={new QueryClient()}>
      {children}
    </QueryClientProvider>
  );
};

const singleVersionSelection: ProcessDefinitionSelection = {
  kind: 'single-version',
  definition: {
    processDefinitionId: 'MyProcess',
    processDefinitionKey: '123',
    name: 'My Process',
    version: 1,
    versionTag: 'MyVersionTag',
    hasStartForm: false,
    tenantId: '<default>',
  },
};

const noMatchSelection: ProcessDefinitionSelection = {
  kind: 'no-match',
};

describe('DiagramHeader', () => {
  beforeEach(() => {
    mockSearchProcessInstances().withSuccess(searchResult([]));
  });

  it('should render header with full data', async () => {
    render(
      <DiagramHeader processDefinitionSelection={singleVersionSelection} />,
      {wrapper: Wrapper},
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
        processDefinitionSelection={{
          ...singleVersionSelection,
          definition: {
            ...singleVersionSelection.definition,
            versionTag: undefined,
          },
        }}
      />,
      {
        wrapper: Wrapper,
      },
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
    render(<DiagramHeader processDefinitionSelection={noMatchSelection} />, {
      wrapper: Wrapper,
    });

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
      <DiagramHeader processDefinitionSelection={singleVersionSelection} />,
      {wrapper: Wrapper},
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
      <DiagramHeader processDefinitionSelection={singleVersionSelection} />,
      {wrapper: Wrapper},
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
