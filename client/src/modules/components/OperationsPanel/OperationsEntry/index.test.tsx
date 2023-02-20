/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useState, useLayoutEffect} from 'react';
import {MemoryRouter} from 'react-router-dom';
import {
  render,
  screen,
  waitFor,
  waitForElementToBeRemoved,
} from 'modules/testing-library';
import {OPERATIONS, mockProps} from './index.setup';
import OperationsEntry from './index';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {MOCK_TIMESTAMP} from 'modules/utils/date/__mocks__/formatDate';
import {panelStatesStore} from 'modules/stores/panelStates';
import {LocationLog} from 'modules/utils/LocationLog';
import {Filters} from 'App/Processes/Filters';

function createWrapper() {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    return (
      <ThemeProvider>
        <MemoryRouter>
          {children}
          <LocationLog />
        </MemoryRouter>
      </ThemeProvider>
    );
  };

  return Wrapper;
}

const FinishingOperationsEntry: React.FC = () => {
  const [finishedCount, setFinishedCount] = useState(0);

  useLayoutEffect(() => {
    setFinishedCount(5);
  }, []);

  return (
    <OperationsEntry
      operation={{
        ...OPERATIONS.EDIT,
        operationsTotalCount: 5,
        operationsFinishedCount: finishedCount,
      }}
    />
  );
};

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
    render(
      <OperationsEntry
        {...mockProps}
        operation={OPERATIONS.DELETE_PROCESS_INSTANCE}
      />,
      {
        wrapper: createWrapper(),
      }
    );

    expect(screen.queryByTestId('progress-bar')).not.toBeInTheDocument();
    expect(screen.getByText(MOCK_TIMESTAMP)).toBeInTheDocument();
    expect(
      screen.getByText('df325d44-6a4c-4428-b017-24f923f1d052')
    ).toBeInTheDocument();
    expect(screen.getByText('Delete')).toBeInTheDocument();
    expect(screen.getByTestId('operation-delete-icon')).toBeInTheDocument();
  });

  it('should render modify operation', () => {
    render(<OperationsEntry {...mockProps} operation={OPERATIONS.MODIFY} />, {
      wrapper: createWrapper(),
    });

    expect(screen.queryByTestId('progress-bar')).not.toBeInTheDocument();
    expect(screen.getByText(MOCK_TIMESTAMP)).toBeInTheDocument();
    expect(
      screen.getByText('df325d44-6a4c-4428-b017-24f923f1d052')
    ).toBeInTheDocument();
    expect(screen.getByText('Modify')).toBeInTheDocument();
    expect(screen.getByTestId('operation-modify-icon')).toBeInTheDocument();
  });

  it('should render delete process definition operation', () => {
    render(
      <OperationsEntry
        {...mockProps}
        operation={OPERATIONS.DELETE_PROCESS_DEFINITION}
      />,
      {
        wrapper: createWrapper(),
      }
    );

    expect(screen.queryByTestId('progress-bar')).not.toBeInTheDocument();
    expect(screen.getByText(MOCK_TIMESTAMP)).toBeInTheDocument();
    expect(
      screen.getByText('5de66f22-a438-40f8-a89c-904g2dgfjm28')
    ).toBeInTheDocument();
    expect(
      screen.getByText('Delete ProcessDefinitionA - version 1')
    ).toBeInTheDocument();
    expect(screen.getByTestId('operation-delete-icon')).toBeInTheDocument();
    expect(screen.getByText('1 instance deleted')).toBeInTheDocument();
  });

  it('should render delete decision definition operation', () => {
    render(
      <OperationsEntry
        {...mockProps}
        operation={OPERATIONS.DELETE_DECISION_DEFINITION}
      />,
      {
        wrapper: createWrapper(),
      }
    );

    expect(screen.getByTestId('progress-bar')).toBeInTheDocument();
    expect(screen.queryByText(MOCK_TIMESTAMP)).not.toBeInTheDocument();
    expect(
      screen.getByText('5de66f22-a438-40f8-a89c-fn298fn23988')
    ).toBeInTheDocument();
    expect(
      screen.getByText('Delete DecisionDefinitionA - version 1')
    ).toBeInTheDocument();
    expect(screen.getByTestId('operation-delete-icon')).toBeInTheDocument();
    expect(screen.getByText('10 instances deleted')).toBeInTheDocument();
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
          ...OPERATIONS.DELETE_PROCESS_INSTANCE,
          instancesCount: 3,
        }}
      />,
      {wrapper: createWrapper()}
    );

    expect(screen.queryByText('3 Instances')).not.toBeInTheDocument();
  });

  it('should filter by Operation and expand Filters Panel', async () => {
    panelStatesStore.toggleFiltersPanel();

    const {user} = render(
      <OperationsEntry
        {...mockProps}
        operation={{
          ...OPERATIONS.EDIT,
          instancesCount: 3,
        }}
      />,
      {wrapper: createWrapper()}
    );

    expect(panelStatesStore.state.isFiltersCollapsed).toBe(true);

    await user.click(screen.getByText('3 Instances'));
    expect(screen.getByTestId('search')).toHaveTextContent(
      /^\?active=true&incidents=true&completed=true&canceled=true&operationId=df325d44-6a4c-4428-b017-24f923f1d052$/
    );

    expect(panelStatesStore.state.isFiltersCollapsed).toBe(false);
  });

  it('should not remove optional operation id filter when operation filter is applied twice', async () => {
    panelStatesStore.toggleFiltersPanel();

    const {user} = render(
      <>
        <OperationsEntry
          {...mockProps}
          operation={{
            ...OPERATIONS.EDIT,
            instancesCount: 3,
          }}
        />
        <Filters />
      </>,
      {wrapper: createWrapper()}
    );

    expect(screen.queryByLabelText(/operation id/i)).not.toBeInTheDocument();

    await user.click(screen.getByText('3 Instances'));
    expect(await screen.findByLabelText(/operation id/i)).toBeInTheDocument();

    await user.click(screen.getByText('3 Instances'));
    expect(screen.getByLabelText(/operation id/i)).toBeInTheDocument();
  });

  it('should fake the first 10% progress', async () => {
    render(
      <OperationsEntry
        operation={{
          ...OPERATIONS.EDIT,
          operationsTotalCount: 10,
          operationsFinishedCount: 0,
        }}
      />,
      {wrapper: createWrapper()}
    );

    expect(screen.getByTestId('progress-bar')).toHaveStyleRule('width', '0%');

    await waitFor(() =>
      expect(screen.getByTestId('progress-bar')).toHaveStyleRule('width', '10%')
    );
  });

  it('should render 50% progress and fake progress', async () => {
    jest.useFakeTimers();
    render(
      <OperationsEntry
        operation={{
          ...OPERATIONS.EDIT,
          operationsTotalCount: 10,
          operationsFinishedCount: 5,
        }}
      />,
      {wrapper: createWrapper()}
    );

    await waitFor(() =>
      expect(screen.getByTestId('progress-bar')).toHaveStyleRule('width', '50%')
    );
    await waitFor(() =>
      expect(screen.getByTestId('progress-bar')).toHaveStyleRule('width', '55%')
    );
    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should render 100% progress and hide progress bar', async () => {
    jest.useFakeTimers();
    render(<FinishingOperationsEntry />, {wrapper: createWrapper()});

    await waitFor(() =>
      expect(screen.getByTestId('progress-bar')).toHaveStyleRule(
        'width',
        '100%'
      )
    );
    expect(screen.queryByText(MOCK_TIMESTAMP)).not.toBeInTheDocument();
    await waitForElementToBeRemoved(screen.getByTestId('progress-bar'));
    expect(screen.getByText(MOCK_TIMESTAMP)).toBeInTheDocument();
    jest.clearAllTimers();
    jest.useRealTimers();
  });
});
