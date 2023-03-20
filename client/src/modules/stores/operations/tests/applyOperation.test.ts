/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {operationsStore} from '../';
import {createBatchOperation, operations} from 'modules/testUtils';
import {mockFetchBatchOperations} from 'modules/mocks/api/fetchBatchOperations';
import {mockApplyOperation} from 'modules/mocks/api/processInstances/operations';

describe('Apply Operation', () => {
  afterEach(() => {
    operationsStore.reset();
  });

  it('should prepend operations when an operation is applied', async () => {
    const newOperation = createBatchOperation();

    mockFetchBatchOperations().withSuccess(operations);
    mockApplyOperation().withSuccess(newOperation);

    await operationsStore.fetchOperations();
    expect(operationsStore.state.operations).toEqual(operations);

    await operationsStore.applyOperation({
      instanceId: '1',
      payload: {operationType: 'CANCEL_PROCESS_INSTANCE'},
      onError: () => {},
    });
    expect(operationsStore.state.operations).toEqual([
      newOperation,
      ...operations,
    ]);
  });

  it('should not prepend operations and call error callback when a server error occurred', async () => {
    mockFetchBatchOperations().withSuccess(operations);
    mockApplyOperation().withServerError();

    await operationsStore.fetchOperations();
    expect(operationsStore.state.operations).toEqual(operations);

    const mockOnError = jest.fn();

    await operationsStore.applyOperation({
      instanceId: '1',
      payload: {operationType: 'CANCEL_PROCESS_INSTANCE'},
      onError: mockOnError,
    });
    expect(operationsStore.state.operations).toEqual(operations);
    expect(mockOnError).toHaveBeenCalled();
  });

  it('should not prepend operations and call error callback when a network error occurred', async () => {
    mockFetchBatchOperations().withSuccess(operations);
    mockApplyOperation().withNetworkError();

    await operationsStore.fetchOperations();
    expect(operationsStore.state.operations).toEqual(operations);

    const mockOnError = jest.fn();

    await operationsStore.applyOperation({
      instanceId: '1',
      payload: {operationType: 'CANCEL_PROCESS_INSTANCE'},
      onError: mockOnError,
    });
    expect(operationsStore.state.operations).toEqual(operations);
    expect(mockOnError).toHaveBeenCalled();
  });
});
