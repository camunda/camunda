/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {waitFor} from 'modules/testing-library';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {createInstance} from 'modules/testUtils';
import {flowNodeInstanceStore} from '../';
import {modificationsStore} from '../../modifications';
import {PROCESS_INSTANCE_ID, mockFlowNodeInstances} from './mocks';
import {mockFetchFlowNodeInstances} from 'modules/mocks/api/fetchFlowNodeInstances';

describe('polling', () => {
  let pollInstancesSpy: jest.SpyInstance;
  let stopPollingSpy: jest.SpyInstance;

  beforeEach(() => {
    mockFetchFlowNodeInstances().withSuccess(mockFlowNodeInstances.level1);

    pollInstancesSpy = jest.spyOn(flowNodeInstanceStore, 'pollInstances');
    stopPollingSpy = jest.spyOn(flowNodeInstanceStore, 'stopPolling');

    jest.useFakeTimers();
  });

  afterEach(() => {
    pollInstancesSpy.mockReset();
    stopPollingSpy.mockReset();
    modificationsStore.reset();
    processInstanceDetailsStore.reset();
    flowNodeInstanceStore.reset();
    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should start polling when process instance is active', async () => {
    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'ACTIVE',
      })
    );

    flowNodeInstanceStore.init();

    await waitFor(() => {
      expect(flowNodeInstanceStore.state.status).toBe('fetched');
    });

    // polling
    mockFetchFlowNodeInstances().withSuccess(mockFlowNodeInstances.level1);

    jest.runOnlyPendingTimers();

    await waitFor(() => {
      expect(pollInstancesSpy).toHaveBeenCalled();
    });
  });

  it('should not start polling when process instance is completed', async () => {
    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'COMPLETED',
      })
    );

    flowNodeInstanceStore.init();

    await waitFor(() => {
      expect(flowNodeInstanceStore.state.status).toBe('fetched');
    });

    // polling
    mockFetchFlowNodeInstances().withSuccess(mockFlowNodeInstances.level1);

    jest.runOnlyPendingTimers();
    expect(pollInstancesSpy).not.toHaveBeenCalled();
  });

  it('should stop polling after process instance has finished', async () => {
    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'ACTIVE',
      })
    );

    flowNodeInstanceStore.init();

    await waitFor(() => {
      expect(flowNodeInstanceStore.state.status).toBe('fetched');
    });

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'COMPLETED',
      })
    );

    jest.runOnlyPendingTimers();
    expect(stopPollingSpy).toHaveBeenCalled();
    expect(pollInstancesSpy).not.toHaveBeenCalled();
  });

  it('should not start polling after flow node instances are fetched during modification mode', async () => {
    modificationsStore.enableModificationMode();
    mockFetchFlowNodeInstances().withServerError();

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'ACTIVE',
      })
    );

    flowNodeInstanceStore.fetchFlowNodeInstances({
      treePath: 'test',
    });

    await waitFor(() =>
      expect(flowNodeInstanceStore.state.status).toBe('error')
    );
    jest.runOnlyPendingTimers();

    mockFetchFlowNodeInstances().withSuccess(mockFlowNodeInstances.level1);

    expect(pollInstancesSpy).not.toHaveBeenCalled();
  });
});
