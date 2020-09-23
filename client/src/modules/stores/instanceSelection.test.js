/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {filters} from './filters';
import {createMemoryHistory} from 'history';
import {instancesDiagram} from './instancesDiagram';
import {instances} from './instances';
import {workflowStatistics} from './workflowStatistics';
import {instanceSelection} from './instanceSelection';
import {DEFAULT_FILTER} from 'modules/constants';
import {groupedWorkflowsMock, mockWorkflowStatistics} from 'modules/testUtils';
import {rest} from 'msw';
import {mockServer} from 'modules/mockServer';

describe('stores/instanceSelection', () => {
  const fetchInstancesSpy = jest.spyOn(instances, 'fetchInstances');
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

    filters.setUrlParameters(historyMock, locationMock);
    await filters.init();
    filters.setFilter(DEFAULT_FILTER);
    fetchInstancesSpy.mockClear();
    instanceSelection.init();
  });
  afterEach(() => {
    filters.reset();
    instancesDiagram.reset();
    instances.reset();
    workflowStatistics.reset();
    instanceSelection.reset();
    jest.clearAllMocks();
  });

  describe('filter observer', () => {
    it('should reset instance selection every time filter changes', () => {
      const instanceSelectionSpy = jest.spyOn(instanceSelection, 'resetState');
      expect(instanceSelectionSpy).toHaveBeenCalledTimes(0);

      filters.setFilter({
        ...DEFAULT_FILTER,
        workflow: 'bigVarProcess',
        version: '1',
      });

      expect(instanceSelectionSpy).toHaveBeenCalledTimes(1);
      filters.setFilter({
        ...DEFAULT_FILTER,
      });
      expect(instanceSelectionSpy).toHaveBeenCalledTimes(2);
    });
  });
});
