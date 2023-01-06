/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  render,
  screen,
  waitForElementToBeRemoved,
} from 'modules/testing-library';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {ViewFullVariableButton} from './index';

describe('<ViewFullVariableButton />', () => {
  it('should load full value', async () => {
    jest.useFakeTimers();
    const mockOnClick: () => Promise<null | string> = jest.fn(function mock() {
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
      {
        wrapper: ThemeProvider,
      }
    );

    await user.click(
      screen.getByRole('button', {
        name: /view full value of foo-variable/i,
      })
    );

    expect(
      screen.getByTestId('view-full-variable-spinner')
    ).toBeInTheDocument();

    jest.runOnlyPendingTimers();

    await waitForElementToBeRemoved(() =>
      screen.getByTestId('view-full-variable-spinner')
    );

    expect(mockOnClick).toHaveBeenCalled();
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(
      screen.getByRole('heading', {name: `Full value of ${mockVariableName}`})
    ).toBeInTheDocument();
    jest.useRealTimers();
  });
});
