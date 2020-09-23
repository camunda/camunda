/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {filters} from './filters';
import {groupedWorkflowsMock} from 'modules/testUtils';
import {createMemoryHistory} from 'history';
import {instancesDiagram} from './instancesDiagram';
import {DEFAULT_FILTER} from 'modules/constants';
import {rest} from 'msw';
import {mockServer} from 'modules/mockServer';

jest.mock('modules/utils/bpmn');

describe('stores/instancesDiagram', () => {
  const fetchWorkflowXmlSpy = jest.spyOn(instancesDiagram, 'fetchWorkflowXml');
  const resetDiagramModelSpy = jest.spyOn(
    instancesDiagram,
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

    filters.setUrlParameters(historyMock, locationMock);
    await filters.init();
    filters.setFilter(DEFAULT_FILTER);
    instancesDiagram.init();
    fetchWorkflowXmlSpy.mockClear();
    resetDiagramModelSpy.mockClear();
  });
  afterEach(() => {
    filters.reset();
    instancesDiagram.reset();
    fetchWorkflowXmlSpy.mockClear();
    resetDiagramModelSpy.mockClear();
  });

  describe('workflow filter autorun', () => {
    it('should fetch workflow xml / reset diagram model according to workflow filter', () => {
      expect(fetchWorkflowXmlSpy).toHaveBeenCalledTimes(0);
      expect(resetDiagramModelSpy).toHaveBeenCalledTimes(0);

      filters.setFilter({
        ...DEFAULT_FILTER,
        workflow: 'bigVarProcess',
        version: '1',
      });

      expect(fetchWorkflowXmlSpy).toHaveBeenCalledTimes(1);
      expect(resetDiagramModelSpy).toHaveBeenCalledTimes(0);

      filters.setFilter({
        ...DEFAULT_FILTER,
      });

      expect(fetchWorkflowXmlSpy).toHaveBeenCalledTimes(1);
      expect(resetDiagramModelSpy).toHaveBeenCalledTimes(1);
    });
  });
});
