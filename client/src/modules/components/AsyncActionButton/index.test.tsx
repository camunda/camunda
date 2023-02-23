/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
    jest.useFakeTimers();
    const BUTTON_CONTENT = 'i am a button';
    const LOADING_DESCRIPTION = 'i am loading';
    const FINISHED_DESCRIPTION = 'done with action';
    const ON_SUCCESS_CALLBACK = jest.fn();

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

    jest.runOnlyPendingTimers();

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

    jest.useFakeTimers();
  });
});
