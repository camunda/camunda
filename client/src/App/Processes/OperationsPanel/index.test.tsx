/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  render,
  screen,
  within,
  waitForElementToBeRemoved,
} from '@testing-library/react';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {OperationsPanel} from './index';
import * as CONSTANTS from './constants';
import {mockOperationFinished, mockOperationRunning} from './index.setup';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {MemoryRouter} from 'react-router-dom';
jest.mock('modules/utils/localStorage', () => ({
  getStateLocally: () => ({
    isFiltersCollapsed: false,
    isOperationsCollapsed: false,
  }),
}));

const Wrapper: React.FC = ({children}) => {
  return (
    <ThemeProvider>
      <MemoryRouter>{children}</MemoryRouter>
    </ThemeProvider>
  );
};

describe('OperationsPanel', () => {
  it('should display empty panel on mount', async () => {
    mockServer.use(
      rest.post('/api/batch-operations', (_, res, ctx) =>
        res.once(ctx.json([]))
      )
    );

    render(<OperationsPanel />, {wrapper: Wrapper});

    expect(
      await screen.findByText(CONSTANTS.EMPTY_MESSAGE)
    ).toBeInTheDocument();
  });

  it('should render skeleton when loading', async () => {
    mockServer.use(
      rest.post('/api/batch-operations', (_, res, ctx) =>
        res.once(ctx.json([]))
      )
    );
    render(<OperationsPanel />, {wrapper: Wrapper});

    expect(screen.getByTestId('skeleton')).toBeInTheDocument();
    await waitForElementToBeRemoved(screen.getByTestId('skeleton'));
  });

  it('should render operation entries', async () => {
    mockServer.use(
      rest.post('/api/batch-operations', (_, res, ctx) =>
        res.once(
          ctx.status(200),
          ctx.json([mockOperationRunning, mockOperationFinished])
        )
      )
    );
    render(<OperationsPanel />, {wrapper: Wrapper});

    await waitForElementToBeRemoved(screen.getByTestId('skeleton'));

    const [firstOperation, secondOperation] =
      screen.getAllByTestId('operations-entry');

    expect(firstOperation).toBeInTheDocument();
    expect(secondOperation).toBeInTheDocument();

    const withinFirstOperation = within(firstOperation!);
    const withinSecondOperation = within(secondOperation!);

    expect(
      withinFirstOperation.getByText(mockOperationRunning.id)
    ).toBeInTheDocument();
    expect(withinFirstOperation.getByText('Retry')).toBeInTheDocument();
    expect(
      withinFirstOperation.getByTestId('operation-retry-icon')
    ).toBeInTheDocument();

    expect(
      withinSecondOperation.getByText(mockOperationFinished.id)
    ).toBeInTheDocument();
    expect(withinSecondOperation.getByText('Cancel')).toBeInTheDocument();
    expect(
      withinSecondOperation.getByTestId('operation-cancel-icon')
    ).toBeInTheDocument();
  });

  it('should show an error message', async () => {
    mockServer.use(
      rest.post('/api/batch-operations', (_, res, ctx) =>
        res.once(ctx.json([]), ctx.status(500))
      )
    );

    const {unmount} = render(<OperationsPanel />, {wrapper: Wrapper});

    expect(
      await screen.findByText('Operations could not be fetched')
    ).toBeInTheDocument();

    unmount();

    mockServer.use(
      rest.post('/api/batch-operations', (_, res) =>
        res.networkError('A network error')
      )
    );

    render(<OperationsPanel />, {wrapper: Wrapper});

    expect(
      await screen.findByText('Operations could not be fetched')
    ).toBeInTheDocument();
  });
});
