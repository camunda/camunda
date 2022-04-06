/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {sequenceFlowsStore} from './sequenceFlows';
import {processInstanceDetailsStore} from './processInstanceDetails';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {createInstance, createSequenceFlows} from 'modules/testUtils';
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
    processInstanceDetailsStore.reset();
  });

  it('should fetch sequence flows when current instance is available', async () => {
    processInstanceDetailsStore.setProcessInstance(
      createInstance({id: '123', state: 'CANCELED'})
    );

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
    processInstanceDetailsStore.setProcessInstance(
      createInstance({id: '123', state: 'ACTIVE'})
    );

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
    processInstanceDetailsStore.setProcessInstance(
      createInstance({id: '123', state: 'CANCELED'})
    );

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

  it('should retry fetch on network reconnection', async () => {
    const eventListeners: any = {};
    const originalEventListener = window.addEventListener;
    window.addEventListener = jest.fn((event: string, cb: any) => {
      eventListeners[event] = cb;
    });

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: '123',
        state: 'ACTIVE',
        processId: '10',
      })
    );
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
              processInstanceId: '2251799813685691',
              activityId: 'SequenceFlow_1sz6737',
            },
          ])
        )
      )
    );

    eventListeners.online();

    await waitFor(() =>
      expect(sequenceFlowsStore.state.items).toEqual([
        'SequenceFlow_0drux68',
        'SequenceFlow_0j6tsnn',
        'SequenceFlow_1dwqvrt',
        'SequenceFlow_1fgekwd',
        'SequenceFlow_1sz6737',
      ])
    );

    window.addEventListener = originalEventListener;
  });
});
