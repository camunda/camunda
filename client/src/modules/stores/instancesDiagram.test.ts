/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {filtersStore} from './filters';
import {groupedWorkflowsMock, multiInstanceWorkflow} from 'modules/testUtils';
import {createMemoryHistory} from 'history';
import {instancesDiagramStore} from './instancesDiagram';
import {DEFAULT_FILTER} from 'modules/constants';
import {rest} from 'msw';
import {mockServer} from 'modules/mockServer';
import {waitFor} from '@testing-library/react';

describe('stores/instancesDiagram', () => {
  beforeEach(async () => {
    const historyMock = createMemoryHistory();
    const locationMock = {pathname: '/instances'};
    mockServer.use(
      rest.get('/api/workflows/:workflowId/xml', (_, res, ctx) =>
        res.once(ctx.text(multiInstanceWorkflow))
      ),
      rest.get('/api/workflows/grouped', (_, res, ctx) =>
        res.once(ctx.json(groupedWorkflowsMock))
      )
    );
    filtersStore.setUrlParameters(historyMock, locationMock);
    await filtersStore.init();
    filtersStore.setFilter(DEFAULT_FILTER);
    instancesDiagramStore.init();
  });
  afterEach(() => {
    filtersStore.reset();
    instancesDiagramStore.reset();
  });

  describe('workflow filter autorun', () => {
    it('should fetch workflow xml / reset diagram model according to workflow filter', async () => {
      expect(instancesDiagramStore.state.diagramModel).toBe(null);

      filtersStore.setFilter({
        ...DEFAULT_FILTER,
        workflow: 'bigVarProcess',
        version: '1',
      });

      await waitFor(() =>
        expect(instancesDiagramStore.state.status).toBe('fetched')
      );

      expect(instancesDiagramStore.state.diagramModel).not.toBe(null);

      filtersStore.setFilter({
        ...DEFAULT_FILTER,
      });

      expect(instancesDiagramStore.state.diagramModel).toBe(null);
    });
  });
});
