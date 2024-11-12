/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {operationsStore} from '../';
import {createBatchOperation, operations} from 'modules/testUtils';
import {mockFetchBatchOperations} from 'modules/mocks/api/fetchBatchOperations';
import {mockApplyBatchOperation} from 'modules/mocks/api/processInstances/operations';

describe('Apply Batch Operation', () => {
  afterEach(() => {
    operationsStore.reset();
  });

  it('should prepend operations when a batch operation is applied', async () => {
    const newOperation = createBatchOperation();

    mockFetchBatchOperations().withSuccess(operations);
    mockApplyBatchOperation().withSuccess(newOperation);

    await operationsStore.fetchOperations();
    expect(operationsStore.state.operations).toEqual(operations);

    const mockOnSuccess = jest.fn();
    await operationsStore.applyBatchOperation({
      operationType: 'CANCEL_PROCESS_INSTANCE',
      query: {ids: [], excludeIds: []},
      onSuccess: mockOnSuccess,
      onError: () => {},
    });
    expect(operationsStore.state.operations).toEqual([
      newOperation,
      ...operations,
    ]);
    expect(mockOnSuccess).toHaveBeenCalled();
  });

  it('should not prepend operations and call error callback when a server error occurred', async () => {
    mockFetchBatchOperations().withSuccess(operations);
    mockApplyBatchOperation().withServerError();

    await operationsStore.fetchOperations();
    expect(operationsStore.state.operations).toEqual(operations);

    const mockOnError = jest.fn();

    await operationsStore.applyBatchOperation({
      operationType: 'CANCEL_PROCESS_INSTANCE',
      query: {ids: [], excludeIds: []},
      onSuccess: jest.fn(),
      onError: mockOnError,
    });

    expect(operationsStore.state.operations).toEqual(operations);
    expect(mockOnError).toHaveBeenCalled();
  });

  it('should not prepend operations and call error callback when a network error occurred', async () => {
    const consoleErrorMock = jest
      .spyOn(global.console, 'error')
      .mockImplementation();

    mockFetchBatchOperations().withSuccess(operations);
    mockApplyBatchOperation().withNetworkError();

    await operationsStore.fetchOperations();
    expect(operationsStore.state.operations).toEqual(operations);

    const mockOnError = jest.fn();

    await operationsStore.applyBatchOperation({
      operationType: 'CANCEL_PROCESS_INSTANCE',
      query: {ids: [], excludeIds: []},
      onSuccess: jest.fn(),
      onError: mockOnError,
    });

    expect(operationsStore.state.operations).toEqual(operations);
    expect(mockOnError).toHaveBeenCalled();

    consoleErrorMock.mockRestore();
  });
});
