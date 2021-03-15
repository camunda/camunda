/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {currentInstanceStore} from './currentInstance';
import {flowNodeSelectionStore} from './flowNodeSelection';
import {flowNodeMetaDataStore, MetaDataEntity} from './flowNodeMetaData';
import {waitFor} from '@testing-library/react';

const WORKFLOW_INSTANCE_ID = '2251799813689404';

const metaData: MetaDataEntity = {
  breadcrumb: [{flowNodeId: 'startEvent', flowNodeType: 'START_EVENT'}],
  flowNodeId: 'ServiceTask_1',
  flowNodeInstanceId: '2251799813689409',
  flowNodeType: 'SERVICE_TASK',
  instanceCount: 5,
  instanceMetadata: null,
};

describe('stores/flowNodeMetaData', () => {
  beforeAll(async () => {
    mockServer.use(
      rest.get(
        `/api/workflow-instances/${WORKFLOW_INSTANCE_ID}`,
        (_, res, ctx) =>
          res.once(
            ctx.json({
              id: WORKFLOW_INSTANCE_ID,
              state: 'ACTIVE',
            })
          )
      )
    );

    await currentInstanceStore.init(WORKFLOW_INSTANCE_ID);
  });

  afterAll(() => {
    currentInstanceStore.reset();
  });

  beforeEach(() => {
    mockServer.use(
      rest.post(
        `/api/workflow-instances/${WORKFLOW_INSTANCE_ID}/flow-node-metadata`,
        (_, res, ctx) => res.once(ctx.json(metaData))
      )
    );
    flowNodeSelectionStore.init();
    flowNodeMetaDataStore.init();
  });

  afterEach(() => {
    flowNodeSelectionStore.reset();
    flowNodeMetaDataStore.reset();
  });

  it('should initially set meta data to null', () => {
    expect(flowNodeMetaDataStore.state.metaData).toBe(null);
  });

  it('should fetch and set meta data', async () => {
    flowNodeSelectionStore.setSelection({
      flowNodeId: 'ServiceTask_1',
      flowNodeInstanceId: '2251799813689409',
    });

    await waitFor(() => {
      expect(flowNodeMetaDataStore.state.metaData).toEqual(metaData);
    });

    flowNodeSelectionStore.setSelection(null);

    await waitFor(() => {
      expect(flowNodeMetaDataStore.state.metaData).toEqual(null);
    });
  });
});
