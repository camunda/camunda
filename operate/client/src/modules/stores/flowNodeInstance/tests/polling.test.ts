/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {waitFor} from 'modules/testing-library';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {createInstance} from 'modules/testUtils';
import {flowNodeInstanceStore} from '../';
import {modificationsStore} from '../../modifications';
import {PROCESS_INSTANCE_ID, mockFlowNodeInstances} from './mocks';
import {mockFetchFlowNodeInstances} from 'modules/mocks/api/fetchFlowNodeInstances';

describe('polling', () => {
  beforeEach(() => {
    mockFetchFlowNodeInstances().withSuccess(mockFlowNodeInstances.level1);

    vi.useFakeTimers({shouldAdvanceTime: true});
  });

  afterEach(() => {
    modificationsStore.reset();
    processInstanceDetailsStore.reset();
    flowNodeInstanceStore.reset();
    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('should start polling when process instance is active', async () => {
    const pollInstancesSpy = vi.spyOn(flowNodeInstanceStore, 'pollInstances');
    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'ACTIVE',
      }),
    );

    flowNodeInstanceStore.init();

    await waitFor(() => {
      expect(flowNodeInstanceStore.state.status).toBe('fetched');
    });

    // polling
    mockFetchFlowNodeInstances().withSuccess(mockFlowNodeInstances.level1);

    vi.runOnlyPendingTimers();

    await waitFor(() => {
      expect(pollInstancesSpy).toHaveBeenCalled();
    });

    await waitFor(() => {
      expect(flowNodeInstanceStore.isPollRequestRunning).toBe(false);
    });
  });

  it('should not start polling when process instance is completed', async () => {
    const pollInstancesSpy = vi.spyOn(flowNodeInstanceStore, 'pollInstances');
    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'COMPLETED',
      }),
    );

    flowNodeInstanceStore.init();

    await waitFor(() => {
      expect(flowNodeInstanceStore.state.status).toBe('fetched');
    });

    // polling
    mockFetchFlowNodeInstances().withSuccess(mockFlowNodeInstances.level1);

    vi.runOnlyPendingTimers();
    expect(pollInstancesSpy).not.toHaveBeenCalled();
  });

  it('should stop polling after process instance has finished', async () => {
    const pollInstancesSpy = vi.spyOn(flowNodeInstanceStore, 'pollInstances');
    const stopPollingSpy = vi.spyOn(flowNodeInstanceStore, 'stopPolling');
    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'ACTIVE',
      }),
    );

    flowNodeInstanceStore.init();

    await waitFor(() => {
      expect(flowNodeInstanceStore.state.status).toBe('fetched');
    });

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'COMPLETED',
      }),
    );

    vi.runOnlyPendingTimers();
    expect(stopPollingSpy).toHaveBeenCalled();
    expect(pollInstancesSpy).not.toHaveBeenCalled();
  });

  it('should not start polling after flow node instances are fetched during modification mode', async () => {
    const pollInstancesSpy = vi.spyOn(flowNodeInstanceStore, 'pollInstances');
    modificationsStore.enableModificationMode();
    mockFetchFlowNodeInstances().withServerError();

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'ACTIVE',
      }),
    );

    flowNodeInstanceStore.fetchFlowNodeInstances({
      treePath: 'test',
    });

    await waitFor(() =>
      expect(flowNodeInstanceStore.state.status).toBe('error'),
    );
    vi.runOnlyPendingTimers();

    mockFetchFlowNodeInstances().withSuccess(mockFlowNodeInstances.level1);

    expect(pollInstancesSpy).not.toHaveBeenCalled();
  });
});
