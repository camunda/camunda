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
    const withinFirstRow = within(rows[0]!);
    const withinSecondRow = within(rows[1]!);

    const firstInstance = mockProcessInstances.processInstances[0]!;
    expect(firstInstance).toBeDefined();

    expect(
      withinFirstRow.getByRole('checkbox', {
        name: `Select instance ${firstInstance.id}`,
      })
    ).toBeInTheDocument();
    expect(
      withinFirstRow.getByText(firstInstance.processName)
    ).toBeInTheDocument();
    expect(
      withinFirstRow.getByRole('link', {
        name: `View instance ${firstInstance.id}`,
      })
    ).toBeInTheDocument();
    expect(
      withinFirstRow.getByText(`Version ${firstInstance.processVersion}`)
    ).toBeInTheDocument();
    expect(withinFirstRow.getByText(MOCK_TIMESTAMP)).toBeInTheDocument();
    expect(
      withinFirstRow.getByRole('link', {
        name: `View parent instance ${firstInstance.parentInstanceId}`,
      })
    ).toBeInTheDocument();
    expect(withinFirstRow.queryByText('None')).not.toBeInTheDocument();
    expect(
      withinFirstRow.getByRole('button', {
        name: `Cancel Instance ${firstInstance.id}`,
      })
    ).toBeInTheDocument();

    const secondInstance = mockProcessInstances.processInstances[1]!;
    expect(secondInstance).toBeDefined();

    expect(
      withinSecondRow.getByRole('checkbox', {
        name: `Select instance ${secondInstance.id}`,
      })
    ).toBeInTheDocument();
    expect(
      withinSecondRow.getByText(secondInstance.processName)
    ).toBeInTheDocument();
    expect(
      withinSecondRow.getByRole('link', {
        name: `View instance ${secondInstance.id}`,
      })
    ).toBeInTheDocument();
    expect(
      withinSecondRow.getByText(`Version ${secondInstance.processVersion}`)
    ).toBeInTheDocument();
    expect(withinSecondRow.getByText(MOCK_TIMESTAMP)).toBeInTheDocument();
    expect(withinSecondRow.getByText('None')).toBeInTheDocument();
    expect(
      withinSecondRow.getByRole('button', {
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
    const withinFirstRow = within(rows[0]!);

    const firstInstance = mockProcessInstances.processInstances[0]!;
    expect(firstInstance).toBeDefined();

    expect(
      withinFirstRow.getByRole('button', {
        name: `Cancel Instance ${firstInstance.id}`,
      })
    ).toBeEnabled();

    userEvent.click(
      withinFirstRow.getByRole('button', {
        name: `Cancel Instance ${firstInstance.id}`,
      })
    );
    expect(screen.getByText(/About to cancel Instance/)).toBeInTheDocument();
    userEvent.click(screen.getByRole('button', {name: 'Apply'}));

    await waitForElementToBeRemoved(
      screen.getByText(/About to cancel Instance/)
    );

    expect(
      withinFirstRow.getByRole('button', {
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
    const withinSecondRow = within(rows[1]!);

    const instanceWithIncident = mockProcessInstances.processInstances[1]!;
    expect(instanceWithIncident).toBeDefined();

    expect(
      withinSecondRow.getByRole('button', {
        name: `Retry Instance ${instanceWithIncident.id}`,
      })
    ).toBeEnabled();

    userEvent.click(
      withinSecondRow.getByRole('button', {
        name: `Retry Instance ${instanceWithIncident.id}`,
      })
    );
    expect(
      withinSecondRow.getByRole('button', {
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
    const withinThirdRow = within(rows[2]!);

    const finishedInstance = mockProcessInstances.processInstances[2]!;
    expect(finishedInstance).toBeDefined();

    expect(
      withinThirdRow.getByRole('button', {
        name: `Delete Instance ${finishedInstance.id}`,
      })
    ).toBeEnabled();

    userEvent.click(
      withinThirdRow.getByRole('button', {
        name: `Delete Instance ${finishedInstance.id}`,
      })
    );
    expect(screen.getByText(/About to delete Instance/)).toBeInTheDocument();
    userEvent.click(screen.getByTestId('delete-button'));

    await waitForElementToBeRemoved(
      screen.getByText(/About to delete Instance/)
    );

    expect(
      withinThirdRow.getByRole('button', {
        name: `Delete Instance ${finishedInstance.id}`,
      })
    ).toBeDisabled();
  });
});
