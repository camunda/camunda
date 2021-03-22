/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Router} from 'react-router-dom';
import {createMemoryHistory} from 'history';
import {render, screen, fireEvent} from '@testing-library/react';
import {OPERATIONS, mockProps} from './index.setup';
import OperationsEntry from './index';
import {ThemeProvider} from 'modules/theme/ThemeProvider';

function createWrapper(history = createMemoryHistory()) {
  const Wrapper: React.FC = ({children}) => {
    return (
      <ThemeProvider>
        <Router history={history}>{children}</Router>
      </ThemeProvider>
    );
  };

  return Wrapper;
}

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
      {wrapper: createWrapper()}
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
      {wrapper: createWrapper()}
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
      {wrapper: createWrapper()}
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
      {wrapper: createWrapper()}
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
      {wrapper: createWrapper()}
    );

    expect(screen.getByText('3 Instances')).toBeInTheDocument();
  });

  it('should filter by Operation', () => {
    const mockHistory = createMemoryHistory();
    render(
      <OperationsEntry
        {...mockProps}
        operation={{
          ...OPERATIONS.EDIT,
          instancesCount: 3,
        }}
      />,
      {wrapper: createWrapper(mockHistory)}
    );

    fireEvent.click(screen.getByText('3 Instances'));
    expect(mockHistory.location.search).toBe(
      '?active=true&incidents=true&completed=true&canceled=true&operationId=df325d44-6a4c-4428-b017-24f923f1d052'
    );
  });
});
