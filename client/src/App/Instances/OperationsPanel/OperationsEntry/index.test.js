/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {render, screen, fireEvent} from '@testing-library/react';
import {DEFAULT_FILTER_CONTROLLED_VALUES} from 'modules/constants';
import {OPERATIONS, mockProps} from './index.setup';
import OperationsEntry from './index';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {filtersStore} from 'modules/stores/filters';

describe('OperationsEntry', () => {
  it('should render retry operation', () => {
    render(
      <OperationsEntry
        {...mockProps}
        operation={{
          ...OPERATIONS.RETRY,
          instancesCount: 1,
        }}
      />,
      {wrapper: ThemeProvider}
    );

    expect(screen.getByTestId('progress-bar')).toBeInTheDocument();
    expect(screen.getByText(OPERATIONS.RETRY.id)).toBeInTheDocument();
    expect(screen.getByText('Retry')).toBeInTheDocument();
    expect(screen.getByTestId('operation-retry-icon')).toBeInTheDocument();
  });

  it('should render cancel operation', () => {
    render(
      <OperationsEntry
        {...mockProps}
        operation={{
          ...OPERATIONS.CANCEL,
          instancesCount: 1,
        }}
      />,
      {wrapper: ThemeProvider}
    );

    expect(screen.queryByTestId('progress-bar')).not.toBeInTheDocument();
    expect(screen.getByText('12 Dec 2018 00:00:00')).toBeInTheDocument();
    expect(screen.getByText(OPERATIONS.CANCEL.id)).toBeInTheDocument();
    expect(screen.getByText('Cancel')).toBeInTheDocument();
    expect(screen.getByTestId('operation-cancel-icon')).toBeInTheDocument();
  });

  it('should render edit operation', () => {
    render(
      <OperationsEntry
        {...mockProps}
        operation={{
          ...OPERATIONS.EDIT,
          instancesCount: 1,
        }}
      />,
      {wrapper: ThemeProvider}
    );

    expect(screen.queryByTestId('progress-bar')).not.toBeInTheDocument();
    expect(screen.getByText('12 Dec 2018 00:00:00')).toBeInTheDocument();
    expect(screen.getByText(OPERATIONS.EDIT.id)).toBeInTheDocument();
    expect(screen.getByText('Edit')).toBeInTheDocument();
    expect(screen.getByTestId('operation-edit-icon')).toBeInTheDocument();
  });

  it('should render instances count when there is one instance', () => {
    render(
      <OperationsEntry
        {...mockProps}
        operation={{
          ...OPERATIONS.EDIT,
          instancesCount: 1,
        }}
      />,
      {wrapper: ThemeProvider}
    );

    expect(screen.getByText('1 Instance')).toBeInTheDocument();
  });

  it('should render instances count when there is more than one instance', () => {
    render(
      <OperationsEntry
        {...mockProps}
        operation={{
          ...OPERATIONS.EDIT,
          instancesCount: 3,
        }}
      />,
      {wrapper: ThemeProvider}
    );

    expect(screen.getByText('3 Instances')).toBeInTheDocument();
  });

  it('should be able to handle instance click', () => {
    render(
      <OperationsEntry
        {...mockProps}
        operation={{
          ...OPERATIONS.EDIT,
          instancesCount: 3,
        }}
      />,
      {wrapper: ThemeProvider}
    );

    expect(filtersStore.state.filter).toEqual({});

    fireEvent.click(screen.getByText('3 Instances'));

    expect(filtersStore.state.filter).toEqual({
      ...DEFAULT_FILTER_CONTROLLED_VALUES,
      active: true,
      incidents: true,
      completed: true,
      canceled: true,
      batchOperationId: 'df325d44-6a4c-4428-b017-24f923f1d052',
    });
  });
});
