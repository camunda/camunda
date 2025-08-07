/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, within} from 'modules/testing-library.ts';
import {Footer} from './index.tsx';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {useEffect} from 'react';
import {MemoryRouter} from 'react-router-dom';
import {tracking} from 'modules/tracking';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';

type Props = {
  children?: React.ReactNode;
};

const Wrapper = ({children}: Props) => {
  useEffect(() => {
    processInstanceMigrationStore.setCurrentStep('elementMapping');
    return processInstanceMigrationStore.reset;
  }, []);
  return (
    <QueryClientProvider client={getMockQueryClient()}>
      <MemoryRouter>
        {children}
        <button
          onClick={() => {
            processInstanceMigrationStore.updateFlowNodeMapping({
              sourceId: 'task1',
              targetId: 'task2',
            });
          }}
        >
          map element
        </button>
      </MemoryRouter>
    </QueryClientProvider>
  );
};

describe('Footer', () => {
  it('should render correct buttons in each step', async () => {
    const {user} = render(<Footer />, {wrapper: Wrapper});

    expect(screen.getByRole('button', {name: 'Next'})).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: 'Exit migration'}),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: 'Back'}),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: 'Confirm'}),
    ).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: /map element/i}));

    await user.click(screen.getByRole('button', {name: 'Next'}));

    expect(
      screen.queryByRole('button', {name: 'Next'}),
    ).not.toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Back'})).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Confirm'})).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: 'Exit migration'}),
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Back'}));

    expect(screen.getByRole('button', {name: 'Next'})).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: 'Back'}),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: 'Confirm'}),
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: 'Exit migration'}),
    ).toBeInTheDocument();
  });

  it('should display confirmation modal on exit migration click', async () => {
    const {user} = render(<Footer />, {wrapper: Wrapper});

    await user.click(screen.getByRole('button', {name: 'Exit migration'}));

    expect(
      screen.getByText(
        /You are about to leave ongoing migration, all planned mapping\/s will be discarded./,
      ),
    ).toBeInTheDocument();
    expect(screen.getByText(/Click “Exit” to proceed./)).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Cancel'}));

    expect(
      screen.queryByText(
        /You are about to leave ongoing migration, all planned mapping\/s will be discarded./,
      ),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText(/Click “Exit” to proceed./),
    ).not.toBeInTheDocument();

    expect(processInstanceMigrationStore.isEnabled).toBe(true);

    await user.click(screen.getByRole('button', {name: 'Exit migration'}));

    await user.click(screen.getByRole('button', {name: 'danger Exit'}));
    expect(processInstanceMigrationStore.isEnabled).toBe(false);
  });

  it('should track confirm button click', async () => {
    const trackSpy = vi.spyOn(tracking, 'track');

    processInstanceMigrationStore.setBatchOperationQuery({
      active: true,
    });
    processInstanceMigrationStore.setTargetProcessDefinitionKey('test-key');

    const {user} = render(<Footer />, {wrapper: Wrapper});

    await user.click(screen.getByRole('button', {name: /map element/i}));
    await user.click(screen.getByRole('button', {name: /next/i}));
    await user.click(screen.getByRole('button', {name: /confirm/i}));

    const withinModal = within(screen.getByRole('dialog'));
    await user.type(withinModal.getByRole('textbox'), 'MIGRATE');
    await user.click(withinModal.getByRole('button', {name: /confirm/i}));

    expect(trackSpy).toHaveBeenCalledWith({
      eventName: 'process-instance-migration-confirmed',
    });
  });
});
