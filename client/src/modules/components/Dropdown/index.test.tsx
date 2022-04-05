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
} from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import {ThemeProvider} from 'modules/theme/ThemeProvider';
import Dropdown from './index';
import Option from './Option';

const stringLabel = 'Some Label';

const mockOnOpen = jest.fn();
const mockOnClick = jest.fn();

describe('Dropdown', () => {
  afterEach(() => {
    mockOnOpen.mockClear();
  });

  it('should show/hide dropdown option', () => {
    render(
      <Dropdown placement={'top'} label={stringLabel} onOpen={mockOnOpen}>
        <Option
          disabled={false}
          onClick={mockOnClick}
          label="Create New Selection"
        />
      </Dropdown>,
      {wrapper: ThemeProvider}
    );

    expect(
      screen.queryByRole('button', {name: 'Create New Selection'})
    ).not.toBeInTheDocument();
    userEvent.click(screen.getByTestId('dropdown-toggle'));
    expect(mockOnOpen).toHaveBeenCalledTimes(1);

    expect(
      screen.getByRole('button', {name: 'Create New Selection'})
    ).toBeInTheDocument();
  });

  it('should display label as string / component', () => {
    const {rerender} = render(
      <Dropdown placement={'top'} label={stringLabel} onOpen={mockOnOpen}>
        <Option
          disabled={false}
          onClick={mockOnClick}
          label="Create New Selection"
        />
      </Dropdown>,
      {wrapper: ThemeProvider}
    );

    expect(screen.getByText(stringLabel)).toBeInTheDocument();

    rerender(
      <Dropdown
        placement={'top'}
        label={<div>{'some other label'}</div>}
        onOpen={mockOnOpen}
      >
        <Option
          disabled={false}
          onClick={mockOnClick}
          label="Create New Selection"
        />
      </Dropdown>
    );

    expect(screen.getByText('some other label')).toBeInTheDocument();
  });

  it('should close the dropdown when clicking anywhere', async () => {
    render(
      <>
        <Dropdown placement={'top'} label={stringLabel} onOpen={mockOnOpen}>
          <Option
            disabled={false}
            onClick={mockOnClick}
            label="Create New Selection"
          />
        </Dropdown>
        <div>somewhere else</div>
      </>,
      {wrapper: ThemeProvider}
    );

    userEvent.click(screen.getByTestId('dropdown-toggle'));

    expect(
      screen.getByRole('button', {name: 'Create New Selection'})
    ).toBeInTheDocument();

    userEvent.click(screen.getByText('somewhere else'));

    await waitForElementToBeRemoved(
      screen.queryByRole('button', {name: 'Create New Selection'})
    );
  });
});
