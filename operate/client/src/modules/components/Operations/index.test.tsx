/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {Operations} from './index';
import {mockFetchCallHierarchy} from 'modules/mocks/api/v2/processInstances/fetchCallHierarchy';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {QueryClientProvider} from '@tanstack/react-query';
import {MemoryRouter} from 'react-router-dom';

const getWrapper = () => {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    return (
      <MemoryRouter>
        <QueryClientProvider client={getMockQueryClient()}>
          {children}
        </QueryClientProvider>
      </MemoryRouter>
    );
  };
  return Wrapper;
};

const PROCESS_INSTANCE_KEY = 'instance_1';

describe('OperationsPresentational', () => {
  beforeEach(() => {
    mockFetchCallHierarchy().withSuccess([]);
  });

  it('should render retry, cancel, modify and delete buttons', () => {
    render(
      <Operations
        operations={[
          {type: 'RESOLVE_INCIDENT', onExecute: vi.fn()},
          {type: 'CANCEL_PROCESS_INSTANCE', onExecute: vi.fn()},
          {type: 'ENTER_MODIFICATION_MODE', onExecute: vi.fn()},
          {type: 'DELETE_PROCESS_INSTANCE', onExecute: vi.fn()},
        ]}
        processInstanceKey={PROCESS_INSTANCE_KEY}
      />,
      {wrapper: getWrapper()},
    );

    expect(
      screen.getByTitle(`Retry Instance ${PROCESS_INSTANCE_KEY}`),
    ).toBeInTheDocument();
    expect(
      screen.getByTitle(`Cancel Instance ${PROCESS_INSTANCE_KEY}`),
    ).toBeInTheDocument();
    expect(
      screen.getByTitle(`Modify Instance ${PROCESS_INSTANCE_KEY}`),
    ).toBeInTheDocument();
    expect(
      screen.getByTitle(`Delete Instance ${PROCESS_INSTANCE_KEY}`),
    ).toBeInTheDocument();
  });

  it('should render no buttons when operations array is empty', () => {
    render(
      <Operations operations={[]} processInstanceKey={PROCESS_INSTANCE_KEY} />,
      {wrapper: getWrapper()},
    );

    expect(
      screen.queryByTitle(`Retry Instance ${PROCESS_INSTANCE_KEY}`),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByTitle(`Cancel Instance ${PROCESS_INSTANCE_KEY}`),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByTitle(`Modify Instance ${PROCESS_INSTANCE_KEY}`),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByTitle(`Delete Instance ${PROCESS_INSTANCE_KEY}`),
    ).not.toBeInTheDocument();
  });

  it('should execute resolve incident operation', async () => {
    const mockOnExecute = vi.fn();

    const {user} = render(
      <Operations
        operations={[{type: 'RESOLVE_INCIDENT', onExecute: mockOnExecute}]}
        processInstanceKey={PROCESS_INSTANCE_KEY}
      />,
      {wrapper: getWrapper()},
    );

    await user.click(screen.getByRole('button', {name: /retry instance/i}));

    expect(mockOnExecute).toHaveBeenCalled();
  });

  it('should execute cancel operation', async () => {
    const mockOnExecute = vi.fn();

    const {user} = render(
      <Operations
        operations={[
          {type: 'CANCEL_PROCESS_INSTANCE', onExecute: mockOnExecute},
        ]}
        processInstanceKey={PROCESS_INSTANCE_KEY}
      />,
      {wrapper: getWrapper()},
    );

    await user.click(screen.getByRole('button', {name: /cancel instance/i}));
    await user.click(screen.getByRole('button', {name: /apply/i}));

    expect(mockOnExecute).toHaveBeenCalled();
  });

  it('should execute delete operation', async () => {
    const mockOnExecute = vi.fn();

    const {user} = render(
      <Operations
        operations={[
          {type: 'DELETE_PROCESS_INSTANCE', onExecute: mockOnExecute},
        ]}
        processInstanceKey={PROCESS_INSTANCE_KEY}
      />,
      {wrapper: getWrapper()},
    );

    await user.click(screen.getByRole('button', {name: /delete instance/i}));
    await user.click(screen.getByRole('button', {name: /danger delete/i}));

    expect(mockOnExecute).toHaveBeenCalled();
  });

  it('should execute modify operation', async () => {
    const mockOnExecute = vi.fn();

    const {user} = render(
      <Operations
        operations={[
          {type: 'ENTER_MODIFICATION_MODE', onExecute: mockOnExecute},
        ]}
        processInstanceKey={PROCESS_INSTANCE_KEY}
      />,
      {wrapper: getWrapper()},
    );

    await user.click(screen.getByRole('button', {name: /modify instance/i}));

    expect(mockOnExecute).toHaveBeenCalled();
  });

  it('should show loading state', () => {
    render(
      <Operations
        operations={[{type: 'CANCEL_PROCESS_INSTANCE', onExecute: vi.fn()}]}
        processInstanceKey={PROCESS_INSTANCE_KEY}
        isLoading
      />,
      {wrapper: getWrapper()},
    );

    expect(screen.getByTestId('operation-spinner')).toBeInTheDocument();
  });

  it('should hide loading state', () => {
    const {rerender} = render(
      <Operations
        operations={[{type: 'CANCEL_PROCESS_INSTANCE', onExecute: vi.fn()}]}
        processInstanceKey={PROCESS_INSTANCE_KEY}
        isLoading
      />,
      {wrapper: getWrapper()},
    );

    expect(screen.getByTestId('operation-spinner')).toBeInTheDocument();

    rerender(
      <Operations
        operations={[{type: 'CANCEL_PROCESS_INSTANCE', onExecute: vi.fn()}]}
        processInstanceKey={PROCESS_INSTANCE_KEY}
        isLoading={false}
      />,
    );

    expect(screen.queryByTestId('operation-spinner')).not.toBeInTheDocument();
  });

  it('should show cancel confirmation modal', async () => {
    const {user} = render(
      <Operations
        operations={[{type: 'CANCEL_PROCESS_INSTANCE', onExecute: vi.fn()}]}
        processInstanceKey={PROCESS_INSTANCE_KEY}
      />,
      {wrapper: getWrapper()},
    );

    await user.click(screen.getByRole('button', {name: /cancel instance/i}));

    const modalText = `About to cancel Instance ${PROCESS_INSTANCE_KEY}. In case there are called instances, these will be canceled too.`;
    expect(screen.getByText(modalText)).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Apply'})).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Cancel'})).toBeInTheDocument();
  });

  it('should show delete confirmation modal', async () => {
    const {user} = render(
      <Operations
        operations={[{type: 'DELETE_PROCESS_INSTANCE', onExecute: vi.fn()}]}
        processInstanceKey={PROCESS_INSTANCE_KEY}
      />,
      {wrapper: getWrapper()},
    );

    await user.click(screen.getByRole('button', {name: /delete instance/i}));

    const modalText = `About to delete Instance ${PROCESS_INSTANCE_KEY}.`;
    expect(screen.getByText(modalText)).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: /danger delete/i}),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Cancel'})).toBeInTheDocument();
  });

  it('should show resolve incident confirmation modal', async () => {
    const mockOnExecute = vi.fn();

    const {user} = render(
      <Operations
        operations={[{type: 'RESOLVE_INCIDENT', onExecute: mockOnExecute}]}
        processInstanceKey={PROCESS_INSTANCE_KEY}
      />,
      {wrapper: getWrapper()},
    );

    await user.click(screen.getByRole('button', {name: /retry instance/i}));

    expect(mockOnExecute).toHaveBeenCalled();
  });

  it('should show root instance warning modal when call hierarchy has parents', async () => {
    mockFetchCallHierarchy().withSuccess([
      {
        processInstanceKey: '3',
        processDefinitionName: 'some root process',
        processDefinitionKey: 'process-key',
      },
      {
        processInstanceKey: '2',
        processDefinitionName: 'some parent process',
        processDefinitionKey: 'process-key',
      },
    ]);

    const {user} = render(
      <Operations
        operations={[{type: 'CANCEL_PROCESS_INSTANCE', onExecute: vi.fn()}]}
        processInstanceKey={PROCESS_INSTANCE_KEY}
      />,
      {wrapper: getWrapper()},
    );

    await user.click(screen.getByRole('button', {name: /cancel instance/i}));

    expect(
      await screen.findByTestId('passive-cancellation-modal'),
    ).toBeInTheDocument();

    const modalText =
      /To cancel this instance, the root instance.*needs to be canceled/;
    expect(screen.getByText(modalText)).toBeInTheDocument();

    expect(
      screen.queryByRole('button', {name: 'Cancel'}),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: 'Apply'}),
    ).not.toBeInTheDocument();
  });

  it('should not trigger callbacks', async () => {
    const mockOnExecute = vi.fn();

    const {user} = render(
      <Operations
        operations={[
          {
            type: 'CANCEL_PROCESS_INSTANCE',
            onExecute: mockOnExecute,
            disabled: true,
          },
          {
            type: 'DELETE_PROCESS_INSTANCE',
            onExecute: mockOnExecute,
            disabled: true,
          },
          {
            type: 'RESOLVE_INCIDENT',
            onExecute: mockOnExecute,
            disabled: true,
          },
          {
            type: 'ENTER_MODIFICATION_MODE',
            onExecute: mockOnExecute,
            disabled: true,
          },
        ]}
        processInstanceKey={PROCESS_INSTANCE_KEY}
      />,
      {wrapper: getWrapper()},
    );

    const cancelButton = screen.getByRole('button', {name: /cancel instance/i});
    const deleteButton = screen.getByRole('button', {name: /delete instance/i});
    const retryButton = screen.getByRole('button', {name: /retry instance/i});
    const modifyButton = screen.getByRole('button', {name: /modify instance/i});

    expect(cancelButton).toBeDisabled();
    expect(deleteButton).toBeDisabled();
    expect(retryButton).toBeDisabled();
    expect(modifyButton).toBeDisabled();

    await user.click(cancelButton);
    await user.click(deleteButton);
    await user.click(retryButton);
    await user.click(modifyButton);

    expect(mockOnExecute).not.toHaveBeenCalled();
  });
});
