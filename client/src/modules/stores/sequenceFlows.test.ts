/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {sequenceFlowsStore} from './sequenceFlows';
import {currentInstanceStore} from './currentInstance';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {createSequenceFlows} from 'modules/testUtils';
import {waitFor} from '@testing-library/react';

describe('stores/sequenceFlows', () => {
  const mockSequenceFlows = createSequenceFlows();

  beforeEach(() => {
    mockServer.use(
      rest.get('/api/process-instances/:id/sequence-flows', (_, res, ctx) =>
        res.once(ctx.json(mockSequenceFlows))
      )
    );
  });

  afterEach(() => {
    sequenceFlowsStore.reset();
    currentInstanceStore.reset();
  });

  it('should fetch sequence flows when current instance is available', async () => {
    currentInstanceStore.setCurrentInstance({id: 123, state: 'CANCELED'});

    sequenceFlowsStore.init();

    await waitFor(() =>
      expect(sequenceFlowsStore.state.items).toEqual([
        'SequenceFlow_0drux68',
        'SequenceFlow_0j6tsnn',
        'SequenceFlow_1dwqvrt',
        'SequenceFlow_1fgekwd',
      ])
    );
  });

  it('should poll when current instance is running', async () => {
    currentInstanceStore.setCurrentInstance({id: 123, state: 'ACTIVE'});

    jest.useFakeTimers();
    sequenceFlowsStore.init();

    await waitFor(() =>
      expect(sequenceFlowsStore.state.items).toEqual([
        'SequenceFlow_0drux68',
        'SequenceFlow_0j6tsnn',
        'SequenceFlow_1dwqvrt',
        'SequenceFlow_1fgekwd',
      ])
    );

    mockServer.use(
      rest.get('/api/process-instances/:id/sequence-flows', (_, res, ctx) =>
        res.once(
          ctx.json([
            ...mockSequenceFlows,
            {
              processInstanceId: '2251799813693731',
              activityId: 'SequenceFlow_1sz6737',
            },
          ])
        )
      )
    );

    jest.runOnlyPendingTimers();

    await waitFor(() =>
      expect(sequenceFlowsStore.state.items).toEqual([
        'SequenceFlow_0drux68',
        'SequenceFlow_0j6tsnn',
        'SequenceFlow_1dwqvrt',
        'SequenceFlow_1fgekwd',
        'SequenceFlow_1sz6737',
      ])
    );

    mockServer.use(
      rest.get('/api/process-instances/:id/sequence-flows', (_, res, ctx) =>
        res.once(
          ctx.json([
            ...mockSequenceFlows,
            {
              processInstanceId: '2251799813685691',
              activityId: 'SequenceFlow_1sz6737',
            },
          ])
        )
      )
    );

    jest.runOnlyPendingTimers();

    await waitFor(() =>
      expect(sequenceFlowsStore.state.items).toEqual([
        'SequenceFlow_0drux68',
        'SequenceFlow_0j6tsnn',
        'SequenceFlow_1dwqvrt',
        'SequenceFlow_1fgekwd',
        'SequenceFlow_1sz6737',
      ])
    );

    // stop polling when current instance is no longer running
    currentInstanceStore.setCurrentInstance({id: 123, state: 'CANCELED'});

    jest.runOnlyPendingTimers();
    jest.runOnlyPendingTimers();

    await waitFor(() =>
      expect(sequenceFlowsStore.state.items).toEqual([
        'SequenceFlow_0drux68',
        'SequenceFlow_0j6tsnn',
        'SequenceFlow_1dwqvrt',
        'SequenceFlow_1fgekwd',
        'SequenceFlow_1sz6737',
      ])
    );

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should reset store', async () => {
    await sequenceFlowsStore.fetchProcessSequenceFlows('1');

    await waitFor(() =>
      expect(sequenceFlowsStore.state.items).toEqual([
        'SequenceFlow_0drux68',
        'SequenceFlow_0j6tsnn',
        'SequenceFlow_1dwqvrt',
        'SequenceFlow_1fgekwd',
      ])
    );

    sequenceFlowsStore.reset();

    expect(sequenceFlowsStore.state.items).toEqual([]);
  });
});
