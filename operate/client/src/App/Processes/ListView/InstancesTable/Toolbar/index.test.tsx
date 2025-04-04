/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {Toolbar} from '.';
import {MemoryRouter} from 'react-router-dom';
import {batchModificationStore} from 'modules/stores/batchModification';
import {useEffect} from 'react';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';

type Props = {
  children?: React.ReactNode;
};

const Wrapper = ({children}: Props) => {
  useEffect(() => {
    return batchModificationStore.reset;
  });
  return (
    <QueryClientProvider client={getMockQueryClient()}>
      <MemoryRouter>
        {children}
        <button onClick={batchModificationStore.enable}>
          Enter batch modification mode
        </button>
      </MemoryRouter>
    </QueryClientProvider>
  );
};

describe('<ProcessOperations />', () => {
  it('should not display toolbar if selected instances count is 0 ', async () => {
    render(<Toolbar selectedInstancesCount={0} />, {wrapper: Wrapper});

    expect(screen.queryByText(/items selected/i)).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: 'Retry'}),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: 'Cancel'}),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: 'Discard'}),
    ).not.toBeInTheDocument();
  });

  it('should display toolbar with action buttons', async () => {
    const {rerender} = render(<Toolbar selectedInstancesCount={1} />, {
      wrapper: Wrapper,
    });

    expect(screen.getAllByRole('button', {name: 'Cancel'}).length).toBe(2);
    expect(screen.getByRole('button', {name: 'Retry'}));
    expect(screen.getByRole('button', {name: 'Discard'}));
    expect(screen.getByText('1 item selected'));

    rerender(<Toolbar selectedInstancesCount={10} />);

    expect(screen.getByText('10 items selected'));
  });

  it('should disable cancel and retry in batch modification mode', async () => {
    const {user} = render(<Toolbar selectedInstancesCount={1} />, {
      wrapper: Wrapper,
    });

    await user.click(
      screen.getByRole('button', {name: /enter batch modification mode/i}),
    );

    expect(
      screen.getByRole('button', {
        description: 'Not available in batch modification mode',
        name: 'Cancel',
      }),
    ).toBeDisabled();
    expect(
      screen.getByRole('button', {
        description: 'Not available in batch modification mode',
        name: 'Retry',
      }),
    ).toBeDisabled();
    expect(screen.getByRole('button', {name: 'Discard'})).toBeEnabled();
  });
});
