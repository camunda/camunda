/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import {observer} from 'mobx-react';
import {render, screen} from 'modules/testing-library';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelectionV2';
import {batchModificationStore} from 'modules/stores/batchModification';
import {BatchModificationFooter} from '../index';
import {MemoryRouter} from 'react-router-dom';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';

vi.mock('modules/hooks/useCallbackPrompt', () => {
  return {
    useCallbackPrompt: () => ({
      shouldInterrupt: false,
      confirmNavigation: vi.fn(),
      cancelNavigation: vi.fn(),
    }),
  };
});

vi.mock('../BatchModificationSummaryModal', () => ({
  BatchModificationSummaryModal: () => (
    <div>MockedBatchModificationSummaryModal</div>
  ),
}));

const Wrapper: React.FC<{children?: React.ReactNode}> = observer(
  ({children}) => {
    useEffect(() => {
      processInstancesSelectionStore.init();
      processInstancesSelectionStore.setRuntime({
        totalProcessInstancesCount: 10,
        visibleIds: ['123', '456', '789'],
        visibleRunningIds: ['123', '456', '789'],
      });

      return () => {
        processInstancesSelectionStore.reset();
        batchModificationStore.reset();
      };
    });

    return (
      <QueryClientProvider client={getMockQueryClient()}>
        <MemoryRouter>
          {children}
          <button
            onClick={processInstancesSelectionStore.selectAllProcessInstances}
          >
            Toggle select all instances
          </button>
          <button
            onClick={() =>
              processInstancesSelectionStore.selectProcessInstance('123')
            }
          >
            select single instance
          </button>
          <button
            onClick={() => {
              batchModificationStore.selectTargetElement('startEvent');
            }}
          >
            select target flow node
          </button>
        </MemoryRouter>
      </QueryClientProvider>
    );
  },
);

describe('BatchModificationFooter', () => {
  it('should disable apply button when no instances are selected', async () => {
    const {user} = render(<BatchModificationFooter />, {wrapper: Wrapper});

    await user.click(
      screen.getByRole('button', {name: /select target flow node/i}),
    );
    expect(
      screen.getByRole('button', {name: /apply modification/i}),
    ).toBeDisabled();
  });

  it('should enable apply button when all instances are selected', async () => {
    const {user} = render(<BatchModificationFooter />, {wrapper: Wrapper});

    await user.click(
      screen.getByRole('button', {name: /select target flow node/i}),
    );

    // select all instances
    await user.click(
      screen.getByRole('button', {name: /toggle select all instances/i}),
    );
    expect(
      screen.getByRole('button', {name: /apply modification/i}),
    ).toBeEnabled();

    // deselect all instances
    await user.click(
      screen.getByRole('button', {name: /toggle select all instances/i}),
    );
    expect(
      screen.getByRole('button', {name: /apply modification/i}),
    ).toBeDisabled();
  });

  it('should enable apply button when one instance is selected', async () => {
    const {user} = render(<BatchModificationFooter />, {wrapper: Wrapper});

    await user.click(
      screen.getByRole('button', {name: /select target flow node/i}),
    );
    await user.click(
      screen.getByRole('button', {name: /select single instance/i}),
    );
    expect(
      screen.getByRole('button', {name: /apply modification/i}),
    ).toBeEnabled();
  });

  it('should enable apply button when one instance is excluded', async () => {
    const {user} = render(<BatchModificationFooter />, {wrapper: Wrapper});

    await user.click(
      screen.getByRole('button', {name: /select target flow node/i}),
    );
    await user.click(
      screen.getByRole('button', {name: /toggle select all instances/i}),
    );
    await user.click(
      screen.getByRole('button', {name: /select single instance/i}),
    );
    expect(
      screen.getByRole('button', {name: /apply modification/i}),
    ).toBeEnabled();
  });

  it('should disable apply button when no target flow node is selected', async () => {
    const {user} = render(<BatchModificationFooter />, {wrapper: Wrapper});

    await user.click(
      screen.getByRole('button', {name: /select single instance/i}),
    );
    expect(
      screen.getByRole('button', {name: /apply modification/i}),
    ).toBeDisabled();

    await user.click(
      screen.getByRole('button', {name: /select target flow node/i}),
    );
    expect(
      screen.getByRole('button', {name: /apply modification/i}),
    ).toBeEnabled();
  });

  it('should render modal on button click', async () => {
    const {user} = render(<BatchModificationFooter />, {wrapper: Wrapper});

    await user.click(
      screen.getByRole('button', {name: /select target flow node/i}),
    );
    await user.click(
      screen.getByRole('button', {name: /select single instance/i}),
    );

    expect(
      screen.queryByText('MockedBatchModificationSummaryModal'),
    ).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: /apply modification/i}));

    expect(
      screen.getByText('MockedBatchModificationSummaryModal'),
    ).toBeInTheDocument();
  });
});
