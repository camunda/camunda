/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {init, fetchMetaData} from './flowNodeMetadata';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {fetchFlowNodeMetaData} from 'modules/api/processInstances/fetchFlowNodeMetaData';
import {reaction} from 'mobx';
import {ProcessDefinitionStatistic} from '@vzeta/camunda-api-zod-schemas/operate';
import {mockProcessInstances} from 'modules/testUtils';

jest.mock('modules/api/processInstances/fetchFlowNodeMetaData', () => ({
  fetchFlowNodeMetaData: jest.fn(),
}));

jest.mock('mobx', () => ({
  ...jest.requireActual('mobx'),
  reaction: jest.fn(),
}));

jest.mock('modules/stores/flowNodeMetaData', () => ({
  flowNodeMetaDataStore: {
    setMetaData: jest.fn(),
    startFetching: jest.fn(),
    handleSuccess: jest.fn(),
    handleError: jest.fn(),
    retryOnConnectionLost: jest.fn((fn) => fn),
  },
}));

jest.mock('modules/stores/flowNodeSelection', () => ({
  flowNodeSelectionStore: {
    state: {
      selection: null,
    },
  },
}));

jest.mock('modules/stores/processInstanceDetails', () => ({
  processInstanceDetailsStore: {
    state: {
      processInstance: null,
    },
  },
}));

jest.mock('modules/stores/modifications', () => ({
  modificationsStore: {
    isModificationModeEnabled: false,
  },
}));

describe('flowNodeMetadata', () => {
  afterEach(() => {
    jest.clearAllMocks();
  });

  describe('init', () => {
    it('should set up a reaction to flowNodeSelectionStore.state.selection', () => {
      const statistics: ProcessDefinitionStatistic[] = [
        {
          flowNodeId: 'node1',
          active: 0,
          canceled: 0,
          incidents: 0,
          completed: 0,
        },
        {
          flowNodeId: 'node2',
          active: 0,
          canceled: 0,
          incidents: 0,
          completed: 0,
        },
      ];

      const disposer = jest.fn();
      (reaction as jest.Mock).mockReturnValue(disposer);

      init(statistics);

      expect(reaction).toHaveBeenCalledWith(
        expect.any(Function),
        expect.any(Function),
      );

      const selectionReaction = (reaction as jest.Mock).mock.calls[0][1];
      flowNodeSelectionStore.state.selection = {flowNodeId: 'node1'};
      selectionReaction(flowNodeSelectionStore.state.selection);

      expect(flowNodeMetaDataStore.setMetaData).toHaveBeenCalledWith(null);
    });
  });

  describe('fetchMetaData', () => {
    it('should not fetch metadata if isPlaceholder is true', async () => {
      const statistics: ProcessDefinitionStatistic[] = [
        {
          flowNodeId: 'node1',
          active: 0,
          canceled: 0,
          incidents: 0,
          completed: 0,
        },
      ];
      await fetchMetaData(statistics, {
        isPlaceholder: true,
        flowNodeId: 'node1',
      });

      expect(fetchFlowNodeMetaData).not.toHaveBeenCalled();
    });

    it('should not fetch metadata if processInstanceId is undefined', async () => {
      const statistics: ProcessDefinitionStatistic[] = [
        {
          flowNodeId: 'node1',
          active: 0,
          canceled: 0,
          incidents: 0,
          completed: 0,
        },
      ];
      processInstanceDetailsStore.state.processInstance = null;

      await fetchMetaData(statistics, {
        flowNodeId: 'node1',
      });

      expect(fetchFlowNodeMetaData).not.toHaveBeenCalled();
    });

    it('should not fetch metadata if flowNodeId is undefined', async () => {
      const statistics: ProcessDefinitionStatistic[] = [
        {
          flowNodeId: 'node1',
          active: 0,
          canceled: 0,
          incidents: 0,
          completed: 0,
        },
      ];
      processInstanceDetailsStore.state.processInstance =
        mockProcessInstances.processInstances[0] || null;

      await fetchMetaData(statistics, {});

      expect(fetchFlowNodeMetaData).not.toHaveBeenCalled();
    });

    it('should fetch metadata and handle success', async () => {
      const statistics: ProcessDefinitionStatistic[] = [
        {
          flowNodeId: 'node1',
          active: 0,
          canceled: 0,
          incidents: 0,
          completed: 0,
        },
      ];
      processInstanceDetailsStore.state.processInstance =
        mockProcessInstances.processInstances[0] || null;

      // Mock fetchFlowNodeMetaData to return the expected data
      (fetchFlowNodeMetaData as jest.Mock).mockResolvedValue({
        isSuccess: true,
        data: {
          instanceMetadata: {
            startDate: '2025-01-01T00:00:00Z',
            endDate: '2025-01-02T00:00:00Z',
            jobDeadline: '2025-01-03T00:00:00Z',
          },
        },
      });

      // Mock formatDate to return formatted dates
      jest.mock('./date', () => ({
        formatDate: jest.fn((date) => {
          if (date === '2025-01-01T00:00:00Z') return '2018-12-12 00:00:00';
          if (date === '2025-01-02T00:00:00Z') return '2018-12-12 00:00:00';
          if (date === '2025-01-03T00:00:00Z') return '2018-12-12 00:00:00';
          return date;
        }),
      }));

      await fetchMetaData(statistics, {
        flowNodeId: 'node1',
      });

      expect(flowNodeMetaDataStore.startFetching).toHaveBeenCalled();
      expect(fetchFlowNodeMetaData).toHaveBeenCalledWith({
        flowNodeId: 'node1',
        processInstanceId: '2251799813685594',
        flowNodeInstanceId: undefined,
        flowNodeType: undefined,
      });
      expect(flowNodeMetaDataStore.handleSuccess).toHaveBeenCalledWith({
        instanceMetadata: {
          startDate: '2018-12-12 00:00:00',
          endDate: '2018-12-12 00:00:00',
          jobDeadline: '2018-12-12 00:00:00',
        },
      });
    });

    it('should handle error if fetch fails', async () => {
      const statistics: ProcessDefinitionStatistic[] = [
        {
          flowNodeId: 'node1',
          active: 0,
          canceled: 0,
          incidents: 0,
          completed: 0,
        },
      ];
      processInstanceDetailsStore.state.processInstance =
        mockProcessInstances.processInstances[0] || null;
      (fetchFlowNodeMetaData as jest.Mock).mockResolvedValue({
        isSuccess: false,
      });

      await fetchMetaData(statistics, {
        flowNodeId: 'node1',
      });

      expect(flowNodeMetaDataStore.startFetching).toHaveBeenCalled();
      expect(fetchFlowNodeMetaData).toHaveBeenCalledWith({
        flowNodeId: 'node1',
        processInstanceId: '2251799813685594',
        flowNodeInstanceId: undefined,
        flowNodeType: undefined,
      });
      expect(flowNodeMetaDataStore.handleError).toHaveBeenCalled();
    });
  });
});
