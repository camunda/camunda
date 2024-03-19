/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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
import {MOCK_TIMESTAMP} from 'modules/utils/date/__mocks__/formatDate';
import {panelStatesStore} from 'modules/stores/panelStates';
import {LocationLog} from 'modules/utils/LocationLog';
import {Filters} from 'App/Processes/ListView/Filters';
import {IS_OPERATIONS_PANEL_IMPROVEMENT_ENABLED} from 'modules/feature-flags';

function createWrapper() {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    return (
      <MemoryRouter>
        {children}
        <LocationLog />
      </MemoryRouter>
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

    expect(screen.getByRole('progressbar')).toBeInTheDocument();
    expect(
      screen.getByText('b42fd629-73b1-4709-befb-7ccd900fb18d'),
    ).toBeInTheDocument();
    expect(screen.getByText('Retry')).toBeInTheDocument();
    expect(screen.getByTestId('operation-retry-icon')).toBeInTheDocument();
  });

  it('should render cancel operation', () => {
    render(<OperationsEntry {...mockProps} operation={OPERATIONS.CANCEL} />, {
      wrapper: createWrapper(),
    });

    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
    expect(screen.getByText(MOCK_TIMESTAMP)).toBeInTheDocument();
    expect(
      screen.getByText('393ad666-d7f0-45c9-a679-ffa0ef82f88a'),
    ).toBeInTheDocument();
    expect(screen.getByText('Cancel')).toBeInTheDocument();
    expect(screen.getByTestId('operation-cancel-icon')).toBeInTheDocument();
  });

  it('should render edit operation', () => {
    render(<OperationsEntry {...mockProps} operation={OPERATIONS.EDIT} />, {
      wrapper: createWrapper(),
    });

    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
    expect(screen.getByText(MOCK_TIMESTAMP)).toBeInTheDocument();
    expect(
      screen.getByText('df325d44-6a4c-4428-b017-24f923f1d052'),
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
      },
    );

    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
    expect(screen.getByText(MOCK_TIMESTAMP)).toBeInTheDocument();
    expect(
      screen.getByText('df325d44-6a4c-4428-b017-24f923f1d052'),
    ).toBeInTheDocument();
    expect(screen.getByText('Delete')).toBeInTheDocument();
    expect(screen.getByTestId('operation-delete-icon')).toBeInTheDocument();
  });

  it('should render modify operation', () => {
    render(<OperationsEntry {...mockProps} operation={OPERATIONS.MODIFY} />, {
      wrapper: createWrapper(),
    });

    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
    expect(screen.getByText(MOCK_TIMESTAMP)).toBeInTheDocument();
    expect(
      screen.getByText('df325d44-6a4c-4428-b017-24f923f1d052'),
    ).toBeInTheDocument();
    expect(screen.getByText('Modify')).toBeInTheDocument();
    expect(screen.getByTestId('operation-modify-icon')).toBeInTheDocument();
  });

  it('should render migrate operation', () => {
    render(<OperationsEntry {...mockProps} operation={OPERATIONS.MIGRATE} />, {
      wrapper: createWrapper(),
    });

    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
    expect(screen.getByText(MOCK_TIMESTAMP)).toBeInTheDocument();
    expect(
      screen.getByText('8ba1a9a7-8537-4af3-97dc-f7249743b20b'),
    ).toBeInTheDocument();
    expect(screen.getByText('Migrate')).toBeInTheDocument();
    expect(screen.getByTestId('operation-migrate-icon')).toBeInTheDocument();
  });

  it('should render delete process definition operation', () => {
    render(
      <OperationsEntry
        {...mockProps}
        operation={OPERATIONS.DELETE_PROCESS_DEFINITION}
      />,
      {
        wrapper: createWrapper(),
      },
    );

    const {name, id} = OPERATIONS.DELETE_PROCESS_DEFINITION;

    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
    expect(screen.getByText(MOCK_TIMESTAMP)).toBeInTheDocument();
    expect(screen.getByText(id)).toBeInTheDocument();
    expect(screen.getByText(`Delete ${name}`)).toBeInTheDocument();
    expect(screen.getByTestId('operation-delete-icon')).toBeInTheDocument();
  });

  it('should render delete decision definition operation', () => {
    render(
      <OperationsEntry
        {...mockProps}
        operation={OPERATIONS.DELETE_DECISION_DEFINITION}
      />,
      {
        wrapper: createWrapper(),
      },
    );

    const {name, id} = OPERATIONS.DELETE_DECISION_DEFINITION;

    expect(screen.getByRole('progressbar')).toBeInTheDocument();
    expect(screen.queryByText(MOCK_TIMESTAMP)).not.toBeInTheDocument();
    expect(screen.getByText(id)).toBeInTheDocument();
    expect(screen.getByText(`Delete ${name}`)).toBeInTheDocument();
    expect(screen.getByTestId('operation-delete-icon')).toBeInTheDocument();
  });

  //This test is duplicated by the new component OperationsEntryStatus and should be removed after BE issue #6294 gets resolved
  it('should not render instances count for delete operation', () => {
    render(
      <OperationsEntry
        {...mockProps}
        operation={{
          ...OPERATIONS.DELETE_PROCESS_INSTANCE,
          instancesCount: 3,
        }}
      />,
      {wrapper: createWrapper()},
    );

    expect(screen.queryByText('3 Instances')).not.toBeInTheDocument();
  });

  (IS_OPERATIONS_PANEL_IMPROVEMENT_ENABLED ? it : it.skip)(
    'should render id link for non-delete instance operations',
    () => {
      render(
        <OperationsEntry
          {...mockProps}
          operation={{
            ...OPERATIONS.EDIT,
            instancesCount: 6,
            failedOperationsCount: 3,
            completedOperationsCount: 3,
          }}
        />,
        {wrapper: createWrapper()},
      );

      expect(
        screen.getByRole('link', {name: OPERATIONS.EDIT.id}),
      ).toBeInTheDocument();
    },
  );

  it('should not render id link for successful delete instance operations', () => {
    render(
      <OperationsEntry
        {...mockProps}
        operation={{
          ...OPERATIONS.DELETE_PROCESS_INSTANCE,
          instancesCount: 1,
          failedOperationsCount: 0,
          completedOperationsCount: 1,
        }}
      />,
      {wrapper: createWrapper()},
    );

    expect(
      screen.getByText(OPERATIONS.DELETE_PROCESS_INSTANCE.id),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('link', {name: OPERATIONS.DELETE_PROCESS_INSTANCE.id}),
    ).not.toBeInTheDocument();
  });

  it('should not render id link when all instances are successful for delete process definition', () => {
    render(
      <OperationsEntry
        {...mockProps}
        operation={{
          ...OPERATIONS.DELETE_PROCESS_DEFINITION,
          instancesCount: 5,
          failedOperationsCount: 0,
          completedOperationsCount: 5,
        }}
      />,
      {wrapper: createWrapper()},
    );

    expect(
      screen.getByText(OPERATIONS.DELETE_PROCESS_DEFINITION.id),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('link', {
        name: OPERATIONS.DELETE_PROCESS_DEFINITION.id,
      }),
    ).not.toBeInTheDocument();
  });

  (IS_OPERATIONS_PANEL_IMPROVEMENT_ENABLED ? it : it.skip)(
    'should filter by Operation and expand Filters Panel',
    async () => {
      panelStatesStore.toggleFiltersPanel();

      const {user} = render(
        <OperationsEntry
          {...mockProps}
          operation={{
            ...OPERATIONS.EDIT,
            instancesCount: 1,
          }}
        />,
        {wrapper: createWrapper()},
      );

      expect(panelStatesStore.state.isFiltersCollapsed).toBe(true);

      await user.click(screen.getByText(OPERATIONS.EDIT.id));
      expect(screen.getByTestId('search')).toHaveTextContent(
        /^\?active=true&incidents=true&completed=true&canceled=true&operationId=df325d44-6a4c-4428-b017-24f923f1d052$/,
      );

      expect(panelStatesStore.state.isFiltersCollapsed).toBe(false);
    },
  );

  (IS_OPERATIONS_PANEL_IMPROVEMENT_ENABLED ? it : it.skip)(
    'should not remove optional operation id filter when operation filter is applied twice',
    async () => {
      const {user} = render(
        <>
          <OperationsEntry
            {...mockProps}
            operation={{
              ...OPERATIONS.EDIT,
              instancesCount: 1,
            }}
          />
          <Filters />
        </>,
        {wrapper: createWrapper()},
      );

      expect(
        screen.queryByLabelText(/^operation id$/i),
      ).not.toBeInTheDocument();

      await user.click(screen.getByText(OPERATIONS.EDIT.id));

      expect(
        await screen.findByLabelText(/^operation id$/i),
      ).toBeInTheDocument();

      await user.click(screen.getByText(OPERATIONS.EDIT.id));
      expect(screen.getByLabelText(/^operation id$/i)).toBeInTheDocument();
    },
  );

  it('should fake the first 10% progress', async () => {
    render(
      <OperationsEntry
        operation={{
          ...OPERATIONS.EDIT,
          operationsTotalCount: 10,
          operationsFinishedCount: 0,
        }}
      />,
      {wrapper: createWrapper()},
    );

    expect(screen.getByRole('progressbar')).toHaveAttribute(
      'aria-valuenow',
      '0',
    );

    await waitFor(() =>
      expect(screen.getByRole('progressbar')).toHaveAttribute(
        'aria-valuenow',
        '10',
      ),
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
      {wrapper: createWrapper()},
    );

    await waitFor(() =>
      expect(screen.getByRole('progressbar')).toHaveAttribute(
        'aria-valuenow',
        '50',
      ),
    );
    await waitFor(() =>
      expect(screen.getByRole('progressbar')).toHaveAttribute(
        'aria-valuenow',
        '55',
      ),
    );
    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should render 100% progress and hide progress bar', async () => {
    jest.useFakeTimers();
    render(<FinishingOperationsEntry />, {wrapper: createWrapper()});

    await waitFor(() =>
      expect(screen.getByRole('progressbar')).toHaveAttribute(
        'aria-valuenow',
        '100',
      ),
    );
    expect(screen.queryByText(MOCK_TIMESTAMP)).not.toBeInTheDocument();
    await waitForElementToBeRemoved(screen.getByRole('progressbar'));
    expect(screen.getByText(MOCK_TIMESTAMP)).toBeInTheDocument();
    jest.clearAllTimers();
    jest.useRealTimers();
  });
});
