/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {AsyncActionButton} from '.';

describe('<AsyncActionButton />', () => {
  it('should show a button', () => {
    const BUTTON_CONTENT = 'i am a button';

    render(
      <AsyncActionButton status="inactive">{BUTTON_CONTENT}</AsyncActionButton>,
    );

    expect(
      screen.getByRole('button', {
        name: BUTTON_CONTENT,
      }),
    ).toBeInTheDocument();
  });

  it('should show a loading action', async () => {
    vi.useFakeTimers();
    const BUTTON_CONTENT = 'i am a button';
    const LOADING_DESCRIPTION = 'i am loading';
    const FINISHED_DESCRIPTION = 'done with action';
    const ON_SUCCESS_CALLBACK = vi.fn();

    const {rerender} = render(
      <AsyncActionButton status="inactive">{BUTTON_CONTENT}</AsyncActionButton>,
    );

    rerender(
      <AsyncActionButton
        status="active"
        inlineLoadingProps={{
          description: LOADING_DESCRIPTION,
        }}
      >
        {BUTTON_CONTENT}
      </AsyncActionButton>,
    );

    expect(screen.getByText(LOADING_DESCRIPTION)).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: BUTTON_CONTENT}),
    ).not.toBeInTheDocument();

    rerender(
      <AsyncActionButton
        status="finished"
        inlineLoadingProps={{
          description: FINISHED_DESCRIPTION,
          onSuccess: ON_SUCCESS_CALLBACK,
        }}
      >
        {BUTTON_CONTENT}
      </AsyncActionButton>,
    );

    vi.runOnlyPendingTimers();

    expect(screen.getByText(FINISHED_DESCRIPTION)).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: BUTTON_CONTENT}),
    ).not.toBeInTheDocument();

    rerender(
      <AsyncActionButton status="inactive">{BUTTON_CONTENT}</AsyncActionButton>,
    );

    expect(
      screen.getByRole('button', {
        name: BUTTON_CONTENT,
      }),
    ).toBeInTheDocument();
    expect(ON_SUCCESS_CALLBACK).toHaveBeenCalled();

    vi.useFakeTimers();
  });
});
