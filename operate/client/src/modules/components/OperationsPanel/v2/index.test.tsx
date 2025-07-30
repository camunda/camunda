/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  render,
  screen,
  within,
  waitForElementToBeRemoved,
} from 'modules/testing-library';
import {OperationsPanel} from './index';
import * as CONSTANTS from './constants';
import {mockOperationFinished, mockOperationRunning} from './index.setup';
import {MemoryRouter} from 'react-router-dom';
import {mockFetchBatchOperations} from 'modules/mocks/api/fetchBatchOperations';

vi.mock('modules/utils/localStorage', async () => {
  const actual = await vi.importActual('modules/utils/localStorage');
  return {
    ...actual,
    getStateLocally: () => ({
      isFiltersCollapsed: false,
      isOperationsCollapsed: false,
    }),
  };
});

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  return <MemoryRouter>{children}</MemoryRouter>;
};

describe('OperationsPanel', () => {
  it('should display empty panel on mount', async () => {
    mockFetchBatchOperations().withSuccess([]);

    render(<OperationsPanel />, {wrapper: Wrapper});

    expect(
      await screen.findByText(CONSTANTS.EMPTY_MESSAGE),
    ).toBeInTheDocument();
  });

  it('should render skeleton when loading', async () => {
    mockFetchBatchOperations().withSuccess([]);

    render(<OperationsPanel />, {wrapper: Wrapper});

    expect(screen.getByTestId('skeleton')).toBeInTheDocument();
    await waitForElementToBeRemoved(screen.queryByTestId('skeleton'));
  });

  it('should render operation entries', async () => {
    mockFetchBatchOperations().withSuccess([
      mockOperationRunning,
      mockOperationFinished,
    ]);

    render(<OperationsPanel />, {wrapper: Wrapper});

    await waitForElementToBeRemoved(screen.queryByTestId('skeleton'));

    const [firstOperation, secondOperation] =
      screen.getAllByTestId('operations-entry');

    expect(firstOperation).toBeInTheDocument();
    expect(secondOperation).toBeInTheDocument();

    const withinFirstOperation = within(firstOperation!);
    const withinSecondOperation = within(secondOperation!);

    expect(
      withinFirstOperation.getByText(mockOperationRunning.id),
    ).toBeInTheDocument();
    expect(withinFirstOperation.getByText('Retry')).toBeInTheDocument();
    expect(
      withinFirstOperation.getByTestId('operation-retry-icon'),
    ).toBeInTheDocument();

    expect(
      withinSecondOperation.getByText(mockOperationFinished.id),
    ).toBeInTheDocument();
    expect(withinSecondOperation.getByText('Cancel')).toBeInTheDocument();
    expect(
      withinSecondOperation.getByTestId('operation-cancel-icon'),
    ).toBeInTheDocument();
  });

  it('should show an error message', async () => {
    const consoleErrorMock = vi
      .spyOn(global.console, 'error')
      .mockImplementation(() => {});

    mockFetchBatchOperations().withServerError();
    const {unmount} = render(<OperationsPanel />, {wrapper: Wrapper});

    expect(
      await screen.findByText('Operations could not be fetched'),
    ).toBeInTheDocument();

    unmount();

    mockFetchBatchOperations().withNetworkError();

    render(<OperationsPanel />, {wrapper: Wrapper});

    expect(
      await screen.findByText('Operations could not be fetched'),
    ).toBeInTheDocument();

    consoleErrorMock.mockRestore();
  });
});
