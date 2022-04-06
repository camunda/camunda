/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {waitFor} from '@testing-library/react';
import {mockDecisionInstances} from 'modules/mocks/mockDecisionInstances';
import {decisionInstancesStore} from './decisionInstances';

describe('decisionInstancesStore', () => {
  afterEach(() => {
    decisionInstancesStore.reset();
  });

  it('should get decision instances', async () => {
    mockServer.use(
      rest.post('/api/decision-instances', (_, res, ctx) =>
        res.once(ctx.json(mockDecisionInstances))
      )
    );

    expect(decisionInstancesStore.state.status).toBe('initial');

    decisionInstancesStore.fetchProcessInstancesFromFilters();

    await waitFor(() =>
      expect(decisionInstancesStore.state.status).toBe('fetched')
    );
    expect(decisionInstancesStore.state.decisionInstances).toEqual(
      mockDecisionInstances.decisionInstances
    );
  });

  it('should fail when getting decision instances', async () => {
    mockServer.use(
      rest.post('/api/decision-instances', (_, res, ctx) =>
        res.once(ctx.status(500))
      )
    );

    expect(decisionInstancesStore.state.status).toBe('initial');

    decisionInstancesStore.fetchProcessInstancesFromFilters();

    await waitFor(() =>
      expect(decisionInstancesStore.state.status).toBe('error')
    );
  });
});
