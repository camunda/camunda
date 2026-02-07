/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {render, screen} from 'modules/testing-library';
import {MemoryRouter} from 'react-router-dom';
import {OperationsActions} from './OperationActions';
import {mockCancelBatchOperation} from 'modules/mocks/api/v2/batchOperations/cancelBatchOperation';
import {mockSuspendBatchOperation} from 'modules/mocks/api/v2/batchOperations/suspendBatchOperation';
import {mockResumeBatchOperation} from 'modules/mocks/api/v2/batchOperations/resumeBatchOperation';
import {notificationsStore} from 'modules/stores/notifications';

vi.mock('modules/stores/notifications', () => ({
  notificationsStore: {
    displayNotification: vi.fn(() => () => {}),
  },
}));

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => (
  <QueryClientProvider client={getMockQueryClient()}>
    <MemoryRouter>{children}</MemoryRouter>
  </QueryClientProvider>
);

const BATCH_OPERATION_KEY = 'migrate-operation-123';

const defaultProps = {
  batchOperationKey: BATCH_OPERATION_KEY,
  batchOperationType: 'MODIFY_PROCESS_INSTANCE' as const,
};

describe('<OperationActions />', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockSuspendBatchOperation().withSuccess(null);
    mockResumeBatchOperation().withSuccess(null);
    mockCancelBatchOperation().withSuccess(null);
  });

  it('should render Suspend and Cancel actions for created state', () => {
    render(
      <OperationsActions {...defaultProps} batchOperationState="CREATED" />,
      {wrapper: Wrapper},
    );

    expect(screen.getByRole('button', {name: /Suspend/i})).toBeInTheDocument();
    expect(screen.getByRole('button', {name: /Options/i})).toBeInTheDocument();
  });

  it('should call cancel mutation when Cancel is clicked', async () => {
    const cancelSpy = vi.fn();
    mockCancelBatchOperation().withSuccess(null, {mockResolverFn: cancelSpy});

    const {user} = render(
      <OperationsActions {...defaultProps} batchOperationState="CREATED" />,
      {wrapper: Wrapper},
    );

    await user.click(screen.getByRole('button', {name: /Options/i}));
    await user.click(screen.getByText('Cancel'));

    expect(cancelSpy).toHaveBeenCalledTimes(1);
  });

  it('should render Suspend and Cancel actions for active state', () => {
    render(
      <OperationsActions {...defaultProps} batchOperationState="ACTIVE" />,
      {wrapper: Wrapper},
    );

    expect(screen.getByRole('button', {name: /Suspend/i})).toBeInTheDocument();
    expect(screen.getByRole('button', {name: /Options/i})).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /Resume/i}),
    ).not.toBeInTheDocument();
  });

  it('should call suspend mutation when Suspend is clicked', async () => {
    const suspendSpy = vi.fn();
    mockSuspendBatchOperation().withSuccess(null, {
      mockResolverFn: suspendSpy,
    });

    const {user} = render(
      <OperationsActions {...defaultProps} batchOperationState="ACTIVE" />,
      {wrapper: Wrapper},
    );

    await user.click(screen.getByRole('button', {name: /Suspend/i}));

    expect(suspendSpy).toHaveBeenCalledTimes(1);
  });

  it('should display notification when suspend operation fails', async () => {
    mockSuspendBatchOperation().withServerError(500);

    const {user} = render(
      <OperationsActions {...defaultProps} batchOperationState="ACTIVE" />,
      {wrapper: Wrapper},
    );

    await user.click(screen.getByRole('button', {name: /Suspend/i}));

    expect(notificationsStore.displayNotification).toHaveBeenCalledTimes(1);
    expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
      kind: 'error',
      title: 'Operation could not be created',
      isDismissable: true,
    });
  });

  it('should render Resume and Cancel actions', () => {
    render(
      <OperationsActions {...defaultProps} batchOperationState="SUSPENDED" />,
      {wrapper: Wrapper},
    );

    expect(screen.getByRole('button', {name: /Resume/i})).toBeInTheDocument();
    expect(screen.getByRole('button', {name: /Options/i})).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /Suspend/i}),
    ).not.toBeInTheDocument();
  });

  it('should call resume mutation when Resume is clicked', async () => {
    const resumeSpy = vi.fn();
    mockResumeBatchOperation().withSuccess(null, {mockResolverFn: resumeSpy});

    const {user} = render(
      <OperationsActions {...defaultProps} batchOperationState="SUSPENDED" />,
      {wrapper: Wrapper},
    );

    await user.click(screen.getByRole('button', {name: /Resume/i}));

    expect(resumeSpy).toHaveBeenCalledTimes(1);
  });

  it('should display notification when resume operation fails', async () => {
    mockResumeBatchOperation().withServerError(500);

    const {user} = render(
      <OperationsActions {...defaultProps} batchOperationState="SUSPENDED" />,
      {wrapper: Wrapper},
    );

    await user.click(screen.getByRole('button', {name: /Resume/i}));

    expect(notificationsStore.displayNotification).toHaveBeenCalledTimes(1);
    expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
      kind: 'error',
      title: 'Operation could not be created',
      isDismissable: true,
    });
  });

  it('should disable Cancel action when mutation is pending', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});
    mockCancelBatchOperation().withDelay(null);

    const {user} = render(
      <OperationsActions {...defaultProps} batchOperationState="SUSPENDED" />,
      {wrapper: Wrapper},
    );

    await user.click(screen.getByRole('button', {name: /Options/i}));

    const cancelMenuItem = screen.getByText('Cancel').closest('button');
    expect(cancelMenuItem).toBeEnabled();

    await user.click(cancelMenuItem!);

    await user.click(screen.getByRole('button', {name: /Options/i}));
    const updatedCancelMenuItem = screen.getByText('Cancel').closest('button');
    expect(updatedCancelMenuItem).toBeDisabled();

    vi.runOnlyPendingTimers();

    vi.useRealTimers();
  });

  it.each(['COMPLETED', 'PARTIALLY_COMPLETED', 'FAILED', 'CANCELED'] as const)(
    'should not render any actions for %s state',
    (state) => {
      render(
        <OperationsActions {...defaultProps} batchOperationState={state} />,
        {wrapper: Wrapper},
      );

      expect(
        screen.queryByRole('button', {name: /Suspend/i}),
      ).not.toBeInTheDocument();
      expect(
        screen.queryByRole('button', {name: /Resume/i}),
      ).not.toBeInTheDocument();
      expect(
        screen.queryByRole('button', {name: /Options/i}),
      ).not.toBeInTheDocument();
    },
  );
});
