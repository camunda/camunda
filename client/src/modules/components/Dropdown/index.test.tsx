/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {
  render,
  screen,
  fireEvent,
  waitForElementToBeRemoved,
} from '@testing-library/react';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import Dropdown from './index';

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
        <Dropdown.Option
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
    fireEvent.click(screen.getByTestId('dropdown-toggle'));
    expect(mockOnOpen).toHaveBeenCalledTimes(1);

    expect(
      screen.getByRole('button', {name: 'Create New Selection'})
    ).toBeInTheDocument();
  });

  it('should display label as string / component', () => {
    const {rerender} = render(
      <Dropdown placement={'top'} label={stringLabel} onOpen={mockOnOpen}>
        <Dropdown.Option
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
        <Dropdown.Option
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
          <Dropdown.Option
            disabled={false}
            onClick={mockOnClick}
            label="Create New Selection"
          />
        </Dropdown>
        <div>somewhere else</div>
      </>,
      {wrapper: ThemeProvider}
    );

    fireEvent.click(screen.getByTestId('dropdown-toggle'));

    expect(
      screen.getByRole('button', {name: 'Create New Selection'})
    ).toBeInTheDocument();

    fireEvent.click(screen.getByText('somewhere else'));

    await waitForElementToBeRemoved(
      screen.queryByRole('button', {name: 'Create New Selection'})
    );
  });
});
