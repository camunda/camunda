/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {waitFor} from '@testing-library/react';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {currentInstanceStore} from 'modules/stores/currentInstance';
import {createMultiInstanceFlowNodeInstances} from 'modules/testUtils';
import {flowNodeInstanceStore} from './flowNodeInstance';

const PROCESS_INSTANCE_ID = 'processInstance';
const mockFlowNodeInstances = createMultiInstanceFlowNodeInstances(
  PROCESS_INSTANCE_ID
);

describe('stores/flowNodeInstance', () => {
  beforeEach(() => {
    mockServer.use(
      rest.post('/api/flow-node-instances', (_, res, ctx) =>
        res.once(ctx.json(mockFlowNodeInstances.level1))
      ),
      rest.post('/api/flow-node-instances', (_, res, ctx) =>
        res.once(ctx.json(mockFlowNodeInstances.level2))
      ),
      rest.post('/api/flow-node-instances', (_, res, ctx) =>
        res.once(ctx.json(mockFlowNodeInstances.level3))
      )
    );
    currentInstanceStore.setCurrentInstance({
      id: PROCESS_INSTANCE_ID,
      state: 'ACTIVE',
    });
  });

  it('should initialize, add and remove nested sub trees from store', async () => {
    flowNodeInstanceStore.init();

    await waitFor(() => {
      expect(flowNodeInstanceStore.state.status).toBe('fetched');
    });

    expect(flowNodeInstanceStore.state.flowNodeInstances).toEqual(
      mockFlowNodeInstances.level1
    );

    await flowNodeInstanceStore.fetchSubTree({
      treePath: `${PROCESS_INSTANCE_ID}/2251799813686156`,
    });

    expect(flowNodeInstanceStore.state.flowNodeInstances).toEqual({
      ...mockFlowNodeInstances.level1,
      ...mockFlowNodeInstances.level2,
    });

    await flowNodeInstanceStore.fetchSubTree({
      treePath: `${PROCESS_INSTANCE_ID}/2251799813686156/2251799813686166`,
    });

    expect(flowNodeInstanceStore.state.flowNodeInstances).toEqual({
      ...mockFlowNodeInstances.level1,
      ...mockFlowNodeInstances.level2,
      ...mockFlowNodeInstances.level3,
    });

    await flowNodeInstanceStore.removeSubTree({
      treePath: `${PROCESS_INSTANCE_ID}/2251799813686156`,
    });

    expect(flowNodeInstanceStore.state.flowNodeInstances).toEqual(
      mockFlowNodeInstances.level1
    );
  });
});
