/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {VariablesPanel} from './index';

describe('<VariablesPanel />', () => {
  it('should have 2 tabs', () => {
    render(<VariablesPanel />, {wrapper: ThemeProvider});

    expect(
      screen.getByRole('button', {
        name: /inputs and outputs/i,
      })
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', {
        name: /result/i,
      })
    ).toBeInTheDocument();
  });

  it('should render the default tab content', () => {
    render(<VariablesPanel />, {wrapper: ThemeProvider});

    expect(
      screen.getByRole('heading', {
        name: /inputs/i,
      })
    ).toBeInTheDocument();
    expect(
      screen.getByRole('heading', {
        name: /outputs/i,
      })
    ).toBeInTheDocument();
  });

  it('should switch tab content', () => {
    render(<VariablesPanel />, {wrapper: ThemeProvider});

    userEvent.click(
      screen.getByRole('button', {
        name: /result/i,
      })
    );

    expect(screen.getByText(/result content/i)).toBeInTheDocument();

    userEvent.click(
      screen.getByRole('button', {
        name: /inputs and outputs/i,
      })
    );

    expect(
      screen.getByRole('heading', {
        name: /inputs/i,
      })
    ).toBeInTheDocument();
    expect(
      screen.getByRole('heading', {
        name: /outputs/i,
      })
    ).toBeInTheDocument();
  });
});
