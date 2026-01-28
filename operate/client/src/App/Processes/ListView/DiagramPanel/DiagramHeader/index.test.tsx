/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {MemoryRouter} from 'react-router-dom';
import {render, screen} from 'modules/testing-library';
import {DiagramHeader} from '.';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';

const mockQueryClient = getMockQueryClient();

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  return (
    <QueryClientProvider client={mockQueryClient}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>
  );
};

describe('DiagramHeader', () => {
  it('should render header with full data', async () => {
    render(
      <DiagramHeader
        processDetails={{
          processName: 'My Process',
          bpmnProcessId: 'MyProcess',
          version: '1',
          versionTag: 'MyVersionTag',
        }}
        processDefinitionId=""
      />,
      {wrapper: Wrapper},
    );

    expect(screen.getByText(/^process name$/i)).toBeInTheDocument();
    expect(screen.getByText(/^my process$/i)).toBeInTheDocument();

    expect(screen.getByText(/^process id$/i)).toBeInTheDocument();
    expect(screen.getByText(/^MyProcess$/i)).toBeInTheDocument();

    expect(screen.getByText(/^version tag$/i)).toBeInTheDocument();
    expect(screen.getByText(/^MyVersionTag$/i)).toBeInTheDocument();

    expect(
      screen.getByRole('button', {name: /delete process definition/i}),
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
        processDefinitionId=""
      />,
      {wrapper: Wrapper},
    );

    expect(screen.getByText(/^process name$/i)).toBeInTheDocument();
    expect(screen.getByText(/^my process$/i)).toBeInTheDocument();

    expect(screen.getByText(/^process id$/i)).toBeInTheDocument();
    expect(screen.getByText(/^MyProcess$/i)).toBeInTheDocument();

    expect(screen.queryByText(/^version tag$/i)).not.toBeInTheDocument();

    expect(
      screen.getByRole('button', {name: /delete process definition/i}),
    ).toBeInTheDocument();
  });

  it('should render header without data', async () => {
    render(
      <DiagramHeader
        processDetails={{
          processName: 'My Process',
        }}
        processDefinitionId=""
      />,
      {wrapper: Wrapper},
    );

    expect(screen.queryByText(/^process name$/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/^process id$/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/^version tag$/i)).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /delete process definition/i}),
    ).not.toBeInTheDocument();
  });
});
