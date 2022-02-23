/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {MemoryRouter} from 'react-router-dom';
import {
  render,
  screen,
  within,
  waitForElementToBeRemoved,
} from '@testing-library/react';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {mockProcessInstances} from 'modules/testUtils';
import {instancesStore} from 'modules/stores/instances';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {MOCK_TIMESTAMP} from 'modules/utils/date/__mocks__/formatDate';
import {Instances} from './';
import userEvent from '@testing-library/user-event';

function createWrapper(initialPath: string = '/') {
  const Wrapper: React.FC = ({children}) => {
    return (
      <ThemeProvider>
        <MemoryRouter initialEntries={[initialPath]}>{children}</MemoryRouter>
      </ThemeProvider>
    );
  };

  return Wrapper;
}

describe('List/Instances', () => {
  beforeEach(() => {
    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(ctx.json(mockProcessInstances))
      )
    );
  });

  afterEach(() => {
    instancesStore.reset();
  });

  it('should render instances list', async () => {
    render(
      <table>
        <Instances />
      </table>,
      {wrapper: createWrapper()}
    );

    instancesStore.fetchInstances({fetchType: 'initial', payload: {query: {}}});

    const rows = await screen.findAllByRole('row');
    expect(rows).toHaveLength(3);

    const firstInstance = mockProcessInstances.processInstances[0];
    expect(
      within(rows[0]).getByRole('checkbox', {
        name: `Select instance ${firstInstance.id}`,
      })
    ).toBeInTheDocument();
    expect(
      within(rows[0]).getByText(firstInstance.processName)
    ).toBeInTheDocument();
    expect(
      within(rows[0]).getByRole('link', {
        name: `View instance ${firstInstance.id}`,
      })
    ).toBeInTheDocument();
    expect(
      within(rows[0]).getByText(`Version ${firstInstance.processVersion}`)
    ).toBeInTheDocument();
    expect(within(rows[0]).getByText(MOCK_TIMESTAMP)).toBeInTheDocument();
    expect(
      within(rows[0]).getByRole('link', {
        name: `View parent instance ${firstInstance.parentInstanceId}`,
      })
    ).toBeInTheDocument();
    expect(within(rows[0]).queryByText('None')).not.toBeInTheDocument();
    expect(
      within(rows[0]).getByRole('button', {
        name: `Cancel Instance ${firstInstance.id}`,
      })
    ).toBeInTheDocument();

    const secondInstance = mockProcessInstances.processInstances[1];
    expect(
      within(rows[1]).getByRole('checkbox', {
        name: `Select instance ${secondInstance.id}`,
      })
    ).toBeInTheDocument();
    expect(
      within(rows[1]).getByText(secondInstance.processName)
    ).toBeInTheDocument();
    expect(
      within(rows[1]).getByRole('link', {
        name: `View instance ${secondInstance.id}`,
      })
    ).toBeInTheDocument();
    expect(
      within(rows[1]).getByText(`Version ${secondInstance.processVersion}`)
    ).toBeInTheDocument();
    expect(within(rows[1]).getByText(MOCK_TIMESTAMP)).toBeInTheDocument();
    expect(within(rows[1]).getByText('None')).toBeInTheDocument();
    expect(
      within(rows[1]).getByRole('button', {
        name: `Cancel Instance ${secondInstance.id}`,
      })
    ).toBeInTheDocument();
  });

  it('should disable cancel operation on click', async () => {
    mockServer.use(
      rest.post('/api/process-instances/:instanceId/operation', (_, res, ctx) =>
        res.once(
          ctx.json({
            id: 'batch-operation-id',
          })
        )
      )
    );

    render(
      <table>
        <Instances />
      </table>,
      {wrapper: createWrapper()}
    );

    instancesStore.fetchInstances({fetchType: 'initial', payload: {query: {}}});

    const rows = await screen.findAllByRole('row');
    expect(rows).toHaveLength(3);

    const firstInstance = mockProcessInstances.processInstances[0];

    expect(
      within(rows[0]).getByRole('button', {
        name: `Cancel Instance ${firstInstance.id}`,
      })
    ).toBeEnabled();

    userEvent.click(
      within(rows[0]).getByRole('button', {
        name: `Cancel Instance ${firstInstance.id}`,
      })
    );
    expect(screen.getByText(/About to cancel Instance/)).toBeInTheDocument();
    userEvent.click(screen.getByRole('button', {name: 'Apply'}));

    await waitForElementToBeRemoved(
      screen.getByText(/About to cancel Instance/)
    );

    expect(
      within(rows[0]).getByRole('button', {
        name: `Cancel Instance ${firstInstance.id}`,
      })
    ).toBeDisabled();
  });

  it('should disable retry operation on click', async () => {
    mockServer.use(
      rest.post('/api/process-instances/:instanceId/operation', (_, res, ctx) =>
        res.once(
          ctx.json({
            id: 'batch-operation-id',
          })
        )
      )
    );

    render(
      <table>
        <Instances />
      </table>,
      {wrapper: createWrapper()}
    );

    instancesStore.fetchInstances({fetchType: 'initial', payload: {query: {}}});

    const rows = await screen.findAllByRole('row');
    expect(rows).toHaveLength(3);

    const instanceWithIncident = mockProcessInstances.processInstances[1];

    expect(
      within(rows[1]).getByRole('button', {
        name: `Retry Instance ${instanceWithIncident.id}`,
      })
    ).toBeEnabled();

    userEvent.click(
      within(rows[1]).getByRole('button', {
        name: `Retry Instance ${instanceWithIncident.id}`,
      })
    );
    expect(
      within(rows[1]).getByRole('button', {
        name: `Retry Instance ${instanceWithIncident.id}`,
      })
    ).toBeDisabled();
  });

  it('should disable delete operation on click', async () => {
    mockServer.use(
      rest.post('/api/process-instances/:instanceId/operation', (_, res, ctx) =>
        res.once(
          ctx.json({
            id: 'batch-operation-id',
          })
        )
      )
    );

    render(
      <table>
        <Instances />
      </table>,
      {wrapper: createWrapper()}
    );

    instancesStore.fetchInstances({fetchType: 'initial', payload: {query: {}}});

    const rows = await screen.findAllByRole('row');
    expect(rows).toHaveLength(3);

    const finishedInstance = mockProcessInstances.processInstances[2];

    expect(
      within(rows[2]).getByRole('button', {
        name: `Delete Instance ${finishedInstance.id}`,
      })
    ).toBeEnabled();

    userEvent.click(
      within(rows[2]).getByRole('button', {
        name: `Delete Instance ${finishedInstance.id}`,
      })
    );
    expect(screen.getByText(/About to delete Instance/)).toBeInTheDocument();
    userEvent.click(screen.getByTestId('delete-button'));

    await waitForElementToBeRemoved(
      screen.getByText(/About to delete Instance/)
    );

    expect(
      within(rows[2]).getByRole('button', {
        name: `Delete Instance ${finishedInstance.id}`,
      })
    ).toBeDisabled();
  });
});
