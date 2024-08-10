/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, act} from 'modules/testing-library';
import {modificationsStore} from 'modules/stores/modifications';
import {Operations} from '../index';
import {INSTANCE, Wrapper} from './mocks';
import {Paths} from 'modules/Routes';

describe('Operations - Cancel Operation', () => {
  it('should show cancel confirmation modal', async () => {
    const modalText =
      /About to cancel Instance instance_1. In case there are called instances, these will be canceled too/i;

    const {user} = render(
      <Operations
        instance={{
          ...INSTANCE,
          state: 'INCIDENT',
        }}
      />,
      {wrapper: Wrapper},
    );

    await user.click(
      screen.getByRole('button', {name: 'Cancel Instance instance_1'}),
    );

    expect(screen.getByText(modalText)).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Apply'})).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Cancel'})).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Cancel'}));

    expect(screen.queryByText(modalText)).not.toBeInTheDocument();
  });

  it('should show modal when trying to cancel called instance', async () => {
    const onOperationMock = jest.fn();

    const modalText =
      /To cancel this instance, the root instance.*needs to be canceled. When the root instance is canceled all the called instances will be canceled automatically/;

    const {user} = render(
      <Operations
        instance={{
          ...INSTANCE,
          state: 'INCIDENT',
          rootInstanceId: '6755399441058622',
        }}
        onOperation={onOperationMock}
      />,
      {wrapper: Wrapper},
    );

    await user.click(
      screen.getByRole('button', {name: 'Cancel Instance instance_1'}),
    );

    expect(screen.getByText(modalText)).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: 'Cancel'}),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: 'Apply'}),
    ).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: /close/i}));

    expect(screen.queryByText(modalText)).not.toBeInTheDocument();
  });

  it('should redirect to linked parent instance', async () => {
    const rootInstanceId = '6755399441058622';

    const {user} = render(
      <Operations
        instance={{
          ...INSTANCE,
          state: 'INCIDENT',
          rootInstanceId,
        }}
      />,
      {
        wrapper: Wrapper,
      },
    );

    await user.click(
      screen.getByRole('button', {name: 'Cancel Instance instance_1'}),
    );

    await user.click(
      screen.getByRole('link', {
        description: `View root instance ${rootInstanceId}`,
      }),
    );

    expect(screen.getByTestId('pathname').textContent).toBe(
      Paths.processInstance(rootInstanceId),
    );
  });

  it('should display helper modal when clicking modify instance, until user clicks do not show', async () => {
    const helperModalText =
      /Process instance modification mode allows you to plan multiple modifications on a process instance/i;

    const {user} = render(
      <Operations
        instance={{...INSTANCE, state: 'INCIDENT'}}
        isInstanceModificationVisible
      />,
      {
        wrapper: Wrapper,
      },
    );

    await user.click(await screen.findByTitle('Modify Instance instance_1'));
    expect(
      screen.queryByTestId('apply-modifications-button'),
    ).not.toBeInTheDocument();

    expect(screen.getByText(helperModalText)).toBeInTheDocument();

    await user.click(
      screen.getByRole('checkbox', {
        name: /Don't show this message next time/i,
      }),
    );
    await user.click(screen.getByRole('button', {name: 'Continue'}));

    expect(screen.queryByText(helperModalText)).not.toBeInTheDocument();

    expect(modificationsStore.state.status).toBe('enabled');

    act(() => {
      modificationsStore.disableModificationMode();
    });

    expect(modificationsStore.state.status).toBe('disabled');

    await user.click(screen.getByTitle('Modify Instance instance_1'));

    expect(modificationsStore.state.status).toBe('enabled');

    expect(screen.queryByText(helperModalText)).not.toBeInTheDocument();
  });
});
