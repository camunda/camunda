/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {processInstanceDetailsStore} from './processInstanceDetails';
import {flowNodeSelectionStore} from './flowNodeSelection';
import {flowNodeMetaDataStore} from './flowNodeMetaData';
import {waitFor} from 'modules/testing-library';
import {modificationsStore} from './modifications';
import {mockFetchProcessInstance} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {createInstance} from 'modules/testUtils';
import type {MetaDataDto} from 'modules/api/processInstances/fetchFlowNodeMetaData';
import {mockFetchFlowNodeMetadata} from 'modules/mocks/api/processInstances/fetchFlowNodeMetaData';
import {init} from 'modules/utils/flowNodeMetadata';

const PROCESS_INSTANCE_ID = '2251799813689404';

const metaData: MetaDataDto = {
  flowNodeId: 'ServiceTask_1',
  flowNodeInstanceId: '2251799813689409',
  flowNodeType: 'SERVICE_TASK',
  instanceCount: 5,
  instanceMetadata: null,
  incident: null,
  incidentCount: 0,
};

describe('stores/flowNodeMetaData', () => {
  beforeAll(async () => {
    mockFetchProcessInstance().withSuccess(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'ACTIVE',
      }),
    );

    await processInstanceDetailsStore.fetchProcessInstance(PROCESS_INSTANCE_ID);
  });

  afterAll(() => {
    processInstanceDetailsStore.reset();
  });

  beforeEach(() => {
    flowNodeSelectionStore.init();
  });

  afterEach(() => {
    flowNodeSelectionStore.reset();
    modificationsStore.reset();
  });

  it('should initially set meta data to null', () => {
    init('process-instance', []);
    expect(flowNodeMetaDataStore.state.metaData).toBe(null);
  });

  it('should fetch and set meta data', async () => {
    mockFetchFlowNodeMetadata().withSuccess(metaData);
    mockFetchFlowNodeMetadata().withSuccess(metaData);

    init('process-instance', []);
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

  it('should retry fetch on network reconnection', async () => {
    mockFetchFlowNodeMetadata().withSuccess(metaData);
    mockFetchFlowNodeMetadata().withSuccess(metaData);
    mockFetchFlowNodeMetadata().withSuccess(metaData);

    const eventListeners: Record<string, () => void> = {};
    vi.spyOn(window, 'addEventListener').mockImplementation(
      (event: string, cb: EventListenerOrEventListenerObject) => {
        eventListeners[event] = cb as () => void;
      },
    );

    init('process-instance', []);
    flowNodeSelectionStore.setSelection({
      flowNodeId: 'ServiceTask_1',
      flowNodeInstanceId: '2251799813689409',
    });

    await waitFor(() => {
      expect(flowNodeMetaDataStore.state.metaData).toEqual(metaData);
    });

    const newMetaData = {
      ...metaData,
      instanceCount: 6,
    };

    mockFetchFlowNodeMetadata().withSuccess(newMetaData);
    mockFetchFlowNodeMetadata().withSuccess(newMetaData);

    eventListeners.online();

    await waitFor(() => {
      expect(flowNodeMetaDataStore.state.metaData).toEqual(newMetaData);
    });
  });

  it('should not fetch metadata in modification mode if flow node does not have any running/finished instances', async () => {
    modificationsStore.enableModificationMode();

    init('process-instance', []);
    flowNodeSelectionStore.setSelection({
      flowNodeId: 'ServiceTask_2',
      flowNodeInstanceId: '2251799813689409',
    });

    expect(flowNodeMetaDataStore.state.metaData).toEqual(null);
  });
});
