/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Router} from 'react-router';
import {createMemoryHistory} from 'history';
import {render, screen, within} from '@testing-library/react';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {mockProcessInstances} from 'modules/testUtils';
import {instancesStore} from 'modules/stores/instances';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {MOCK_TIMESTAMP} from 'modules/utils/date/__mocks__/formatDate';
import {Instances} from './';

const createWrapper =
  (historyMock = createMemoryHistory()) =>
  ({children}: any) =>
    (
      <ThemeProvider>
        <Router history={historyMock}>{children}</Router>
      </ThemeProvider>
    );

describe('List/Instances', () => {
  mockServer.use(
    rest.post('/api/process-instances', (_, res, ctx) =>
      res.once(ctx.json(mockProcessInstances))
    )
  );
  it('should render instances list', async () => {
    render(
      <table>
        <Instances />
      </table>,
      {wrapper: createWrapper()}
    );

    instancesStore.fetchInstances({fetchType: 'initial', payload: {query: {}}});

    const rows = await screen.findAllByRole('row');
    expect(rows).toHaveLength(2);

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
});
