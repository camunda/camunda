/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {filtersStore} from './filters';
import {groupedWorkflowsMock} from 'modules/testUtils';
import {createMemoryHistory} from 'history';
import {instancesDiagramStore} from './instancesDiagram';
import {DEFAULT_FILTER} from 'modules/constants';
import {rest} from 'msw';
import {mockServer} from 'modules/mockServer';

jest.mock('modules/utils/bpmn');

describe('stores/instancesDiagram', () => {
  const fetchWorkflowXmlSpy = jest.spyOn(
    instancesDiagramStore,
    'fetchWorkflowXml'
  );
  const resetDiagramModelSpy = jest.spyOn(
    instancesDiagramStore,
    'resetDiagramModel'
  );

  beforeEach(async () => {
    const historyMock = createMemoryHistory();
    const locationMock = {pathname: '/instances'};

    mockServer.use(
      rest.get('/api/workflows/:workflowId/xml', (_, res, ctx) =>
        res.once(ctx.text(''))
      ),
      rest.get('/api/workflows/grouped', (_, res, ctx) =>
        res.once(ctx.json(groupedWorkflowsMock))
      )
    );

    filtersStore.setUrlParameters(historyMock, locationMock);
    await filtersStore.init();
    filtersStore.setFilter(DEFAULT_FILTER);
    instancesDiagramStore.init();
    fetchWorkflowXmlSpy.mockClear();
    resetDiagramModelSpy.mockClear();
  });
  afterEach(() => {
    filtersStore.reset();
    instancesDiagramStore.reset();
    fetchWorkflowXmlSpy.mockClear();
    resetDiagramModelSpy.mockClear();
  });

  describe('workflow filter autorun', () => {
    it('should fetch workflow xml / reset diagram model according to workflow filter', () => {
      expect(fetchWorkflowXmlSpy).toHaveBeenCalledTimes(0);
      expect(resetDiagramModelSpy).toHaveBeenCalledTimes(0);

      filtersStore.setFilter({
        ...DEFAULT_FILTER,
        workflow: 'bigVarProcess',
        version: '1',
      });

      expect(fetchWorkflowXmlSpy).toHaveBeenCalledTimes(1);
      expect(resetDiagramModelSpy).toHaveBeenCalledTimes(0);

      filtersStore.setFilter({
        ...DEFAULT_FILTER,
      });

      expect(fetchWorkflowXmlSpy).toHaveBeenCalledTimes(1);
      expect(resetDiagramModelSpy).toHaveBeenCalledTimes(1);
    });
  });
});
