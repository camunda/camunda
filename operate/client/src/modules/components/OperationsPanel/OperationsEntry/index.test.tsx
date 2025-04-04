/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState, useLayoutEffect} from 'react';
import {MemoryRouter} from 'react-router-dom';
import {
  render,
  screen,
  waitFor,
  waitForElementToBeRemoved,
} from 'modules/testing-library';
import {OPERATIONS, mockProps} from './index.setup';
import OperationsEntry from './index';
import {MOCK_TIMESTAMP} from 'modules/utils/date/__mocks__/formatDate';
import {panelStatesStore} from 'modules/stores/panelStates';
import {LocationLog} from 'modules/utils/LocationLog';
import {Filters} from 'App/Processes/ListView/Filters';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';

function createWrapper() {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    return (
      <MemoryRouter>
        {children}
        <LocationLog />
      </MemoryRouter>
    );
  };

  return Wrapper;
}

const FinishingOperationsEntry: React.FC = () => {
  const [finishedCount, setFinishedCount] = useState(0);

  useLayoutEffect(() => {
    setFinishedCount(5);
  }, []);

  return (
    <OperationsEntry
      operation={{
        ...OPERATIONS.EDIT,
        operationsTotalCount: 5,
        operationsFinishedCount: finishedCount,
      }}
    />
  );
};

describe('OperationsEntry', () => {
  it('should render retry operation', () => {
    render(<OperationsEntry {...mockProps} operation={OPERATIONS.RETRY} />, {
      wrapper: createWrapper(),
    });

    expect(screen.getByRole('progressbar')).toBeInTheDocument();
    expect(
      screen.getByText('b42fd629-73b1-4709-befb-7ccd900fb18d'),
    ).toBeInTheDocument();
    expect(screen.getByText('Retry')).toBeInTheDocument();
    expect(screen.getByTestId('operation-retry-icon')).toBeInTheDocument();
  });

  it('should render cancel operation', () => {
    render(<OperationsEntry {...mockProps} operation={OPERATIONS.CANCEL} />, {
      wrapper: createWrapper(),
    });

    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
    expect(screen.getByText(MOCK_TIMESTAMP)).toBeInTheDocument();
    expect(
      screen.getByText('393ad666-d7f0-45c9-a679-ffa0ef82f88a'),
    ).toBeInTheDocument();
    expect(screen.getByText('Cancel')).toBeInTheDocument();
    expect(screen.getByTestId('operation-cancel-icon')).toBeInTheDocument();
  });

  it('should render edit operation', () => {
    render(<OperationsEntry {...mockProps} operation={OPERATIONS.EDIT} />, {
      wrapper: createWrapper(),
    });

    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
    expect(screen.getByText(MOCK_TIMESTAMP)).toBeInTheDocument();
    expect(
      screen.getByText('df325d44-6a4c-4428-b017-24f923f1d052'),
    ).toBeInTheDocument();
    expect(screen.getByText('Edit')).toBeInTheDocument();
    expect(screen.getByTestId('operation-edit-icon')).toBeInTheDocument();
  });

  it('should render delete operation', () => {
    render(
      <OperationsEntry
        {...mockProps}
        operation={OPERATIONS.DELETE_PROCESS_INSTANCE}
      />,
      {
        wrapper: createWrapper(),
      },
    );

    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
    expect(screen.getByText(MOCK_TIMESTAMP)).toBeInTheDocument();
    expect(
      screen.getByText('df325d44-6a4c-4428-b017-24f923f1d052'),
    ).toBeInTheDocument();
    expect(screen.getByText('Delete')).toBeInTheDocument();
    expect(screen.getByTestId('operation-delete-icon')).toBeInTheDocument();
  });

  it('should render modify operation', () => {
    render(<OperationsEntry {...mockProps} operation={OPERATIONS.MODIFY} />, {
      wrapper: createWrapper(),
    });

    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
    expect(screen.getByText(MOCK_TIMESTAMP)).toBeInTheDocument();
    expect(
      screen.getByText('df325d44-6a4c-4428-b017-24f923f1d052'),
    ).toBeInTheDocument();
    expect(screen.getByText('Modify')).toBeInTheDocument();
    expect(screen.getByTestId('operation-modify-icon')).toBeInTheDocument();
  });

  it('should render migrate operation', () => {
    render(<OperationsEntry {...mockProps} operation={OPERATIONS.MIGRATE} />, {
      wrapper: createWrapper(),
    });

    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
    expect(screen.getByText(MOCK_TIMESTAMP)).toBeInTheDocument();
    expect(
      screen.getByText('8ba1a9a7-8537-4af3-97dc-f7249743b20b'),
    ).toBeInTheDocument();
    expect(screen.getByText('Migrate')).toBeInTheDocument();
    expect(screen.getByTestId('operation-migrate-icon')).toBeInTheDocument();
  });

  it('should render batch move modification operation', () => {
    render(<OperationsEntry {...mockProps} operation={OPERATIONS.MOVE} />, {
      wrapper: createWrapper(),
    });

    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
    expect(screen.getByText(MOCK_TIMESTAMP)).toBeInTheDocument();
    expect(
      screen.getByText('8ba1a9a7-8537-4af3-97dc-f7249743b20b'),
    ).toBeInTheDocument();
    expect(screen.getByText('Batch Modification')).toBeInTheDocument();
    expect(screen.getByTestId('operation-move-icon')).toBeInTheDocument();
  });

  it('should render delete process definition operation', () => {
    render(
      <OperationsEntry
        {...mockProps}
        operation={OPERATIONS.DELETE_PROCESS_DEFINITION}
      />,
      {
        wrapper: createWrapper(),
      },
    );

    const {name, id} = OPERATIONS.DELETE_PROCESS_DEFINITION;

    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
    expect(screen.getByText(MOCK_TIMESTAMP)).toBeInTheDocument();
    expect(screen.getByText(id)).toBeInTheDocument();
    expect(screen.getByText(`Delete ${name}`)).toBeInTheDocument();
    expect(screen.getByTestId('operation-delete-icon')).toBeInTheDocument();
  });

  it('should render delete decision definition operation', () => {
    render(
      <OperationsEntry
        {...mockProps}
        operation={OPERATIONS.DELETE_DECISION_DEFINITION}
      />,
      {
        wrapper: createWrapper(),
      },
    );

    const {name, id} = OPERATIONS.DELETE_DECISION_DEFINITION;

    expect(screen.getByRole('progressbar')).toBeInTheDocument();
    expect(screen.queryByText(MOCK_TIMESTAMP)).not.toBeInTheDocument();
    expect(screen.getByText(id)).toBeInTheDocument();
    expect(screen.getByText(`Delete ${name}`)).toBeInTheDocument();
    expect(screen.getByTestId('operation-delete-icon')).toBeInTheDocument();
  });

  //This test is duplicated by the new component OperationsEntryStatus and should be removed after BE issue #6294 gets resolved
  it('should not render instances count for delete operation', () => {
    render(
      <OperationsEntry
        {...mockProps}
        operation={{
          ...OPERATIONS.DELETE_PROCESS_INSTANCE,
          instancesCount: 3,
        }}
      />,
      {wrapper: createWrapper()},
    );

    expect(screen.queryByText('3 Instances')).not.toBeInTheDocument();
  });

  it('should render id link for non-delete instance operations', () => {
    render(
      <OperationsEntry
        {...mockProps}
        operation={{
          ...OPERATIONS.EDIT,
          instancesCount: 6,
          failedOperationsCount: 3,
          completedOperationsCount: 3,
        }}
      />,
      {wrapper: createWrapper()},
    );

    expect(
      screen.getByRole('link', {name: OPERATIONS.EDIT.id}),
    ).toBeInTheDocument();
  });

  it('should not render id link for successful delete instance operations', () => {
    render(
      <OperationsEntry
        {...mockProps}
        operation={{
          ...OPERATIONS.DELETE_PROCESS_INSTANCE,
          instancesCount: 1,
          failedOperationsCount: 0,
          completedOperationsCount: 1,
        }}
      />,
      {wrapper: createWrapper()},
    );

    expect(
      screen.getByText(OPERATIONS.DELETE_PROCESS_INSTANCE.id),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('link', {name: OPERATIONS.DELETE_PROCESS_INSTANCE.id}),
    ).not.toBeInTheDocument();
  });

  it('should not render id link when all instances are successful for delete process definition', () => {
    render(
      <OperationsEntry
        {...mockProps}
        operation={{
          ...OPERATIONS.DELETE_PROCESS_DEFINITION,
          instancesCount: 5,
          failedOperationsCount: 0,
          completedOperationsCount: 5,
        }}
      />,
      {wrapper: createWrapper()},
    );

    expect(
      screen.getByText(OPERATIONS.DELETE_PROCESS_DEFINITION.id),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('link', {
        name: OPERATIONS.DELETE_PROCESS_DEFINITION.id,
      }),
    ).not.toBeInTheDocument();
  });

  it('should filter by Operation and expand Filters Panel', async () => {
    panelStatesStore.toggleFiltersPanel();

    const {user} = render(
      <OperationsEntry
        {...mockProps}
        operation={{
          ...OPERATIONS.EDIT,
          instancesCount: 1,
        }}
      />,
      {wrapper: createWrapper()},
    );

    expect(panelStatesStore.state.isFiltersCollapsed).toBe(true);

    await user.click(screen.getByText(OPERATIONS.EDIT.id));
    expect(screen.getByTestId('search')).toHaveTextContent(
      /^\?active=true&incidents=true&completed=true&canceled=true&operationId=df325d44-6a4c-4428-b017-24f923f1d052$/,
    );

    expect(panelStatesStore.state.isFiltersCollapsed).toBe(false);
  });

  it('should not remove optional operation id filter when operation filter is applied twice', async () => {
    const {user} = render(
      <QueryClientProvider client={getMockQueryClient()}>
        <OperationsEntry
          {...mockProps}
          operation={{
            ...OPERATIONS.EDIT,
            instancesCount: 1,
          }}
        />
        <Filters />
      </QueryClientProvider>,
      {wrapper: createWrapper()},
    );

    expect(screen.queryByLabelText(/^operation id$/i)).not.toBeInTheDocument();

    await user.click(screen.getByText(OPERATIONS.EDIT.id));

    expect(await screen.findByLabelText(/^operation id$/i)).toBeInTheDocument();

    await user.click(screen.getByText(OPERATIONS.EDIT.id));
    expect(screen.getByLabelText(/^operation id$/i)).toBeInTheDocument();
  });

  it('should fake the first 10% progress', async () => {
    render(
      <OperationsEntry
        operation={{
          ...OPERATIONS.EDIT,
          endDate: null,
          operationsTotalCount: 10,
          operationsFinishedCount: 0,
        }}
      />,
      {wrapper: createWrapper()},
    );

    expect(screen.getByRole('progressbar')).toHaveAttribute(
      'aria-valuenow',
      '0',
    );

    await waitFor(() =>
      expect(screen.getByRole('progressbar')).toHaveAttribute(
        'aria-valuenow',
        '10',
      ),
    );
  });

  it('should render 50% progress and fake progress', async () => {
    jest.useFakeTimers();
    render(
      <OperationsEntry
        operation={{
          ...OPERATIONS.EDIT,
          endDate: null,
          operationsTotalCount: 10,
          operationsFinishedCount: 5,
        }}
      />,
      {wrapper: createWrapper()},
    );

    await waitFor(() =>
      expect(screen.getByRole('progressbar')).toHaveAttribute(
        'aria-valuenow',
        '50',
      ),
    );
    await waitFor(() =>
      expect(screen.getByRole('progressbar')).toHaveAttribute(
        'aria-valuenow',
        '55',
      ),
    );
    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should render 100% progress and hide progress bar', async () => {
    jest.useFakeTimers();
    render(<FinishingOperationsEntry />, {wrapper: createWrapper()});

    await waitFor(() =>
      expect(screen.getByRole('progressbar')).toHaveAttribute(
        'aria-valuenow',
        '100',
      ),
    );
    expect(screen.queryByText(MOCK_TIMESTAMP)).not.toBeInTheDocument();
    await waitForElementToBeRemoved(screen.getByRole('progressbar'));
    expect(screen.getByText(MOCK_TIMESTAMP)).toBeInTheDocument();
    jest.clearAllTimers();
    jest.useRealTimers();
  });
});
