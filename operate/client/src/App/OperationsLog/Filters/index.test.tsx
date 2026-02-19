/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Filters} from './index';
import {render, screen, waitFor} from 'modules/testing-library';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {mockSearchProcessDefinitions} from 'modules/mocks/api/v2/processDefinitions/searchProcessDefinitions';
import {mockMe} from 'modules/mocks/api/v2/me';
import {createUser} from 'modules/testUtils';
import {getClientConfig} from 'modules/utils/getClientConfig';

vi.mock('modules/utils/getClientConfig', async (importOriginal) => {
  const actual =
    await importOriginal<typeof import('modules/utils/getClientConfig')>();
  return {
    getClientConfig: vi.fn().mockImplementation(actual.getClientConfig),
  };
});

const {getClientConfig: actualGetClientConfig} = await vi.importActual<
  typeof import('modules/utils/getClientConfig')
>('modules/utils/getClientConfig');

const mockGetClientConfig = vi.mocked(getClientConfig);

function getWrapper(initialPath = '/operations-log') {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
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

  return Wrapper;
}

describe('OperationsLog Filters', () => {
  beforeEach(() => {
    mockGetClientConfig.mockReturnValue(actualGetClientConfig());
    mockMe().withSuccess(createUser());
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

  it('should render process filter fields', async () => {
    render(<Filters />, {
      wrapper: getWrapper(),
    });

    expect(screen.getByText('Process')).toBeInTheDocument();
    expect(
      screen.getByPlaceholderText('Process instance key'),
    ).toBeInTheDocument();
  });

  it('should render tenant field when multi-tenancy is enabled', () => {
    mockGetClientConfig.mockReturnValue({
      ...actualGetClientConfig(),
      multiTenancyEnabled: true,
    });

    render(<Filters />, {
      wrapper: getWrapper(),
    });

    expect(
      screen.getByRole('combobox', {name: /Select a tenant/i}),
    ).toHaveTextContent('Select a tenant');
  });

  it('should not render tenant field when multi-tenancy is disabled', () => {
    render(<Filters />, {
      wrapper: getWrapper(),
    });

    expect(
      screen.queryByRole('combobox', {name: 'Tenant'}),
    ).not.toBeInTheDocument();
  });

  it('should render all filter fields', () => {
    render(<Filters />, {
      wrapper: getWrapper(),
    });

    expect(screen.getByText('Process instance key')).toBeInTheDocument();
    expect(screen.getByText('Operation type')).toBeInTheDocument();
    expect(screen.getByText('Entity type')).toBeInTheDocument();
    expect(screen.getByText('Operations status')).toBeInTheDocument();
    expect(screen.getByText('Actor')).toBeInTheDocument();
    expect(screen.getByText('Timestamp date range')).toBeInTheDocument();
  });

  it('should have reset button disabled when filters are empty', () => {
    render(<Filters />, {
      wrapper: getWrapper(),
    });

    expect(screen.getByRole('button', {name: /reset/i})).toBeDisabled();
  });

  it('should parse filters from URL search params', async () => {
    render(<Filters />, {
      wrapper: getWrapper('/operations-log?processInstanceKey=123'),
    });

    expect(screen.getByLabelText(/process instance key/i)).toHaveValue('123');
  });

  it('should enable reset button when filters are applied', () => {
    render(<Filters />, {
      wrapper: getWrapper('/operations-log?processInstanceKey=123'),
    });

    expect(screen.getByRole('button', {name: /reset/i})).not.toBeDisabled();
  });

  it('should reset filters when reset button is clicked', async () => {
    const {user} = render(<Filters />, {
      wrapper: getWrapper('/operations-log?processInstanceKey=123'),
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
      wrapper: getWrapper(),
    });

    const processInstanceKeyInput =
      screen.getByLabelText(/process instance key/i);

    await user.type(processInstanceKeyInput, '12345');

    await waitFor(() => {
      expect(processInstanceKeyInput).toHaveValue('12345');
    });
  });

  it('should handle process definition identifier correctly', async () => {
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
    render(<Filters />, {
      wrapper: getWrapper('/operations-log?process=process1&version=1'),
    });

    await waitFor(() => {
      expect(screen.getByRole('combobox', {name: 'Name'})).toHaveValue(
        'Process 1',
      );

      expect(
        screen.getByRole('combobox', {name: 'Select a Process Version'}),
      ).toHaveAttribute('title', '1');
    });
  });
});
