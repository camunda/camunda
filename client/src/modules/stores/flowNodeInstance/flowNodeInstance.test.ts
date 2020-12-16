/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

// TODO (paddy): move to modules/stores/

import {waitFor} from '@testing-library/react';
import {rest} from 'msw';
import {currentInstanceStore} from 'modules/stores/currentInstance';
import {mockServer} from 'modules/mockServer';
import {createMultiInstanceFlowNodeInstances} from 'modules/testUtils';
import {flowNodeInstanceStore} from './flowNodeInstance';

const WORKFLOW_INSTANCE_ID = 'workflowInstance';
const mockFlowNodeInstances = createMultiInstanceFlowNodeInstances(
  WORKFLOW_INSTANCE_ID
);

describe('stores/flowNodeInstance', () => {
  beforeEach(() => {
    mockServer.use(
      rest.post('/api/flow-node-instances', (_, res, ctx) =>
        res.once(ctx.json([mockFlowNodeInstances.level1]))
      ),
      rest.post('/api/flow-node-instances', (_, res, ctx) =>
        res.once(ctx.json([mockFlowNodeInstances.level2]))
      ),
      rest.post('/api/flow-node-instances', (_, res, ctx) =>
        res.once(ctx.json([mockFlowNodeInstances.level3]))
      )
    );
    currentInstanceStore.setCurrentInstance({
      id: WORKFLOW_INSTANCE_ID,
      state: 'ACTIVE',
    });
  });

  it('should initialize, add and remove nested sub trees from store', async () => {
    flowNodeInstanceStore.init();

    await waitFor(() => {
      expect(flowNodeInstanceStore.state.status).toBe('fetched');
    });

    await flowNodeInstanceStore.fetchSubTree({
      parentTreePath: mockFlowNodeInstances.level1[1].treePath,
    });

    expect(flowNodeInstanceStore.state.flowNodeInstances).toEqual({
      [WORKFLOW_INSTANCE_ID]: [mockFlowNodeInstances.level1],
      [mockFlowNodeInstances.level1[1].treePath]: [
        mockFlowNodeInstances.level2,
      ],
    });

    await flowNodeInstanceStore.fetchSubTree({
      parentTreePath: mockFlowNodeInstances.level2[0].treePath,
    });

    expect(flowNodeInstanceStore.state.flowNodeInstances).toEqual({
      [WORKFLOW_INSTANCE_ID]: [mockFlowNodeInstances.level1],
      [mockFlowNodeInstances.level1[1].treePath]: [
        mockFlowNodeInstances.level2,
      ],
      [mockFlowNodeInstances.level2[0].treePath]: [
        mockFlowNodeInstances.level3,
      ],
    });

    await flowNodeInstanceStore.removeSubTree({
      parentTreePath: mockFlowNodeInstances.level1[1].treePath,
    });

    expect(flowNodeInstanceStore.state.flowNodeInstances).toEqual({
      [WORKFLOW_INSTANCE_ID]: [mockFlowNodeInstances.level1],
    });
  });
});
