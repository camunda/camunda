/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {filtersStore} from './filters';
import {createMemoryHistory} from 'history';
import {instancesDiagramStore} from './instancesDiagram';
import {instancesStore} from './instances';
import {workflowStatisticsStore} from './workflowStatistics';
import {instanceSelectionStore} from './instanceSelection';
import {DEFAULT_FILTER} from 'modules/constants';
import {groupedWorkflowsMock, mockWorkflowStatistics} from 'modules/testUtils';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';

describe('stores/instanceSelection', () => {
  const fetchInstancesSpy = jest.spyOn(instancesStore, 'fetchInstances');
  beforeEach(async () => {
    const historyMock = createMemoryHistory();
    const locationMock = {pathname: '/instances'};

    mockServer.use(
      rest.get('/api/workflows/grouped', (_, res, ctx) =>
        res.once(ctx.json(groupedWorkflowsMock))
      ),
      rest.post('/api/workflow-instances/statistics', (_, res, ctx) =>
        res.once(ctx.json(mockWorkflowStatistics))
      )
    );

    filtersStore.setUrlParameters(historyMock, locationMock);
    await filtersStore.init();
    filtersStore.setFilter(DEFAULT_FILTER);
    fetchInstancesSpy.mockClear();
    instanceSelectionStore.init();
  });
  afterEach(() => {
    filtersStore.reset();
    instancesDiagramStore.reset();
    instancesStore.reset();
    workflowStatisticsStore.reset();
    instanceSelectionStore.reset();
    jest.clearAllMocks();
  });

  describe('filter observer', () => {
    it('should reset instance selection every time filter changes', () => {
      const initialState = {
        selectedInstanceIds: [],
        isAllChecked: false,
        selectionMode: 'INCLUDE',
      };

      expect(instanceSelectionStore.state).toEqual(initialState);

      instanceSelectionStore.setAllChecked(true);
      instanceSelectionStore.setSelectedInstanceIds(['1']);
      instanceSelectionStore.setMode('EXCLUDE');

      expect(instanceSelectionStore.state).toEqual({
        selectedInstanceIds: ['1'],
        isAllChecked: true,
        selectionMode: 'EXCLUDE',
      });

      filtersStore.setFilter({
        ...DEFAULT_FILTER,
        workflow: 'bigVarProcess',
        version: '1',
      });

      expect(instanceSelectionStore.state).toEqual(initialState);
    });
  });
});
