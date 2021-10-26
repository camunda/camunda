/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Router} from 'react-router-dom';
import {createMemoryHistory} from 'history';
import {render, screen} from '@testing-library/react';
import {OPERATIONS, mockProps} from './index.setup';
import OperationsEntry from './index';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import userEvent from '@testing-library/user-event';
import {MOCK_TIMESTAMP} from 'modules/utils/date/__mocks__/formatDate';
import {panelStatesStore} from 'modules/stores/panelStates';

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
    render(<OperationsEntry {...mockProps} operation={OPERATIONS.RETRY} />, {
      wrapper: createWrapper(),
    });

    expect(screen.getByTestId('progress-bar')).toBeInTheDocument();
    expect(
      screen.getByText('b42fd629-73b1-4709-befb-7ccd900fb18d')
    ).toBeInTheDocument();
    expect(screen.getByText('Retry')).toBeInTheDocument();
    expect(screen.getByTestId('operation-retry-icon')).toBeInTheDocument();
  });

  it('should render cancel operation', () => {
    render(<OperationsEntry {...mockProps} operation={OPERATIONS.CANCEL} />, {
      wrapper: createWrapper(),
    });

    expect(screen.queryByTestId('progress-bar')).not.toBeInTheDocument();
    expect(screen.getByText(MOCK_TIMESTAMP)).toBeInTheDocument();
    expect(
      screen.getByText('393ad666-d7f0-45c9-a679-ffa0ef82f88a')
    ).toBeInTheDocument();
    expect(screen.getByText('Cancel')).toBeInTheDocument();
    expect(screen.getByTestId('operation-cancel-icon')).toBeInTheDocument();
  });

  it('should render edit operation', () => {
    render(<OperationsEntry {...mockProps} operation={OPERATIONS.EDIT} />, {
      wrapper: createWrapper(),
    });

    expect(screen.queryByTestId('progress-bar')).not.toBeInTheDocument();
    expect(screen.getByText(MOCK_TIMESTAMP)).toBeInTheDocument();
    expect(
      screen.getByText('df325d44-6a4c-4428-b017-24f923f1d052')
    ).toBeInTheDocument();
    expect(screen.getByText('Edit')).toBeInTheDocument();
    expect(screen.getByTestId('operation-edit-icon')).toBeInTheDocument();
  });

  it('should render delete operation', () => {
    render(<OperationsEntry {...mockProps} operation={OPERATIONS.DELETE} />, {
      wrapper: createWrapper(),
    });

    expect(screen.queryByTestId('progress-bar')).not.toBeInTheDocument();
    expect(screen.getByText(MOCK_TIMESTAMP)).toBeInTheDocument();
    expect(
      screen.getByText('df325d44-6a4c-4428-b017-24f923f1d052')
    ).toBeInTheDocument();
    expect(screen.getByText('Delete')).toBeInTheDocument();
    expect(screen.getByTestId('operation-delete-icon')).toBeInTheDocument();
  });

  it('should render instances count when there is one instance', () => {
    render(<OperationsEntry {...mockProps} operation={OPERATIONS.EDIT} />, {
      wrapper: createWrapper(),
    });

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

  it('should not render instances count for delete operation', () => {
    render(
      <OperationsEntry
        {...mockProps}
        operation={{
          ...OPERATIONS.DELETE,
          instancesCount: 3,
        }}
      />,
      {wrapper: createWrapper()}
    );

    expect(screen.queryByText('3 Instances')).not.toBeInTheDocument();
  });

  it('should filter by Operation and expand Filters Panel', () => {
    panelStatesStore.toggleFiltersPanel();

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

    expect(panelStatesStore.state.isFiltersCollapsed).toBe(true);

    userEvent.click(screen.getByText('3 Instances'));
    expect(mockHistory.location.search).toBe(
      '?active=true&incidents=true&completed=true&canceled=true&operationId=df325d44-6a4c-4428-b017-24f923f1d052'
    );

    expect(panelStatesStore.state.isFiltersCollapsed).toBe(false);
  });
});
