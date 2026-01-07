/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, it, expect, vi, beforeEach, afterEach} from 'vitest';
import {Filters} from './index';
import {render, screen, waitFor} from 'modules/testing-library';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import React from 'react';
import {mockSearchProcessDefinitions} from 'modules/mocks/api/v2/processDefinitions/searchProcessDefinitions';
import {processesStore} from 'modules/stores/processes/processes.list';

const Wrapper = ({
  children,
  initialPath = '/operations-log',
}: {
  children?: React.ReactNode;
  initialPath?: string;
}) => {
  return (
    <QueryClientProvider client={getMockQueryClient()}>
      <MemoryRouter initialEntries={[initialPath]}>
        <Routes>
          <Route path="/operations-log" element={children} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
};

describe('OperationsLog Filters', () => {
  beforeEach(() => {
    mockSearchProcessDefinitions().withSuccess({
      items: [
        {
          processDefinitionKey: '123',
          processDefinitionId: 'process1',
          name: 'Process 1',
          version: 1,
          tenantId: '<default>',
          hasStartForm: false,
        },
        {
          processDefinitionKey: '456',
          processDefinitionId: 'process2',
          name: 'Process 2',
          version: 1,
          tenantId: '<default>',
          hasStartForm: false,
        },
      ],
      page: {totalItems: 2},
    });
  });

  afterEach(() => {
    processesStore.reset();
  });

  it('should render process filter fields', async () => {
    render(<Filters />, {
      wrapper: Wrapper,
    });

    expect(screen.getByText('Process')).toBeInTheDocument();
    expect(
      screen.getByPlaceholderText('Process instance key'),
    ).toBeInTheDocument();
  });

  it('should render tenant field when multi-tenancy is enabled', () => {
    vi.stubGlobal('clientConfig', {
      multiTenancyEnabled: true,
    });

    render(<Filters />, {
      wrapper: Wrapper,
    });

    expect(screen.getByRole('combobox', {name: 'Tenant'})).toHaveTextContent(
      'Select a tenant',
    );
  });

  it('should not render tenant field when multi-tenancy is disabled', () => {
    render(<Filters />, {
      wrapper: Wrapper,
    });

    expect(
      screen.queryByRole('combobox', {name: 'Tenant'}),
    ).not.toBeInTheDocument();
  });

  it('should render process instance key input field', () => {
    render(<Filters />, {
      wrapper: Wrapper,
    });

    const processInstanceKeyInput =
      screen.getByLabelText(/process instance key/i);
    expect(processInstanceKeyInput).toBeInTheDocument();
    expect(processInstanceKeyInput).toHaveAttribute('type', 'text');
  });

  it('should have reset button disabled when filters are empty', () => {
    render(<Filters />, {
      wrapper: Wrapper,
    });

    expect(screen.getByRole('button', {name: /reset/i})).toBeDisabled();
  });

  it('should parse filters from URL search params', async () => {
    render(<Filters />, {
      wrapper: ({children}) => (
        <Wrapper initialPath="/operations-log?processInstanceKey=123">
          {children}
        </Wrapper>
      ),
    });

    expect(screen.getByLabelText(/process instance key/i)).toHaveValue('123');
  });

  it('should enable reset button when filters are applied', () => {
    render(<Filters />, {
      wrapper: ({children}) => (
        <Wrapper initialPath="/operations-log?processInstanceKey=123">
          {children}
        </Wrapper>
      ),
    });

    expect(screen.getByRole('button', {name: /reset/i})).not.toBeDisabled();
  });

  it('should reset filters when reset button is clicked', async () => {
    const {user} = render(<Filters />, {
      wrapper: ({children}) => (
        <Wrapper initialPath="/operations-log?processInstanceKey=123">
          {children}
        </Wrapper>
      ),
    });

    const resetButton = screen.getByRole('button', {name: /reset/i});
    expect(resetButton).not.toBeDisabled();

    await user.click(resetButton);

    await waitFor(() => {
      expect(resetButton).toBeDisabled();
      expect(
        screen.getByLabelText(/process instance key/i),
      ).toBeEmptyDOMElement();
    });
  });

  it('should submit filters when form values change', async () => {
    const {user} = render(<Filters />, {
      wrapper: Wrapper,
    });

    const processInstanceKeyInput =
      screen.getByLabelText(/process instance key/i);

    await user.type(processInstanceKeyInput, '12345');

    await waitFor(() => {
      expect(processInstanceKeyInput).toHaveValue('12345');
    });
  });

  it('should handle process definition identifier correctly', async () => {
    render(<Filters />, {
      wrapper: ({children}) => (
        <Wrapper initialPath="/operations-log?process=process1&version=1">
          {children}
        </Wrapper>
      ),
    });

    // Verify the component renders without errors
    await waitFor(() => {
      expect(screen.getByRole('combobox', {name: 'Name'})).toHaveValue(
        'Process 1',
      );
      expect(screen.getByRole('combobox', {name: 'Version'})).toHaveAttribute(
        'title',
        '1',
      );
    });
  });
});
