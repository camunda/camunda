/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {render, screen} from '@testing-library/react';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import ColumnHeader from './index';

describe('ColumnHeader', () => {
  const mockPropsWithSorting = {
    active: false,
    label: 'Start Time',
    onSort: jest.fn(),
    sortKey: 'startDate',
  };

  it('should render a button if the column is sortable', () => {
    render(<ColumnHeader {...mockPropsWithSorting} />, {
      wrapper: ThemeProvider,
    });
    expect(screen.getByText(mockPropsWithSorting.label)).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: 'Sort by startDate'})
    ).toBeInTheDocument();
  });

  it('should only render the text if the column is not sortable', () => {
    render(<ColumnHeader label="Start time" />, {wrapper: ThemeProvider});
    expect(screen.getByText('Start time')).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: 'Sort by startDate'})
    ).not.toBeInTheDocument();
  });
});
