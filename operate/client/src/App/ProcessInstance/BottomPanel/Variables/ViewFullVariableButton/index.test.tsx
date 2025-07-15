/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  render,
  screen,
  waitForElementToBeRemoved,
} from 'modules/testing-library';
import {ViewFullVariableButton} from './index';

describe('<ViewFullVariableButton />', () => {
  it('should load full value', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});
    const mockOnClick = vi.fn(function mock(): Promise<null | string> {
      return new Promise((resolve) => {
        setTimeout(() => resolve('foo'), 0);
      });
    });
    const mockVariableName = 'foo-variable';

    const {user} = render(
      <ViewFullVariableButton
        onClick={mockOnClick}
        variableName={mockVariableName}
      />,
    );

    await user.click(
      screen.getByRole('button', {
        name: /view full value of foo-variable/i,
      }),
    );

    expect(
      screen.getByTestId('variable-operation-spinner'),
    ).toBeInTheDocument();

    vi.runOnlyPendingTimers();

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('variable-operation-spinner'),
    );

    expect(mockOnClick).toHaveBeenCalled();
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(
      screen.getByRole('heading', {name: `Full value of ${mockVariableName}`}),
    ).toBeInTheDocument();
    vi.useRealTimers();
  });
});
