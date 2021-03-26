/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {MemoryRouter, Route} from 'react-router-dom';
import {
  render,
  waitForElementToBeRemoved,
  screen,
  waitFor,
} from '@testing-library/react';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {testData} from './index.setup';
import {mockSequenceFlows} from './TopPanel/index.setup';
import {PAGE_TITLE} from 'modules/constants';
import {getProcessName} from 'modules/utils/instance';
import {Instance} from './index';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {createMultiInstanceFlowNodeInstances} from 'modules/testUtils';

jest.mock('modules/utils/bpmn');

type Props = {
  children?: React.ReactNode;
};

const processInstancesMock = createMultiInstanceFlowNodeInstances('4294980768');

const Wrapper: React.FC<Props> = ({children}) => {
  return (
    <ThemeProvider>
      <MemoryRouter initialEntries={['/instances/4294980768']}>
        <Route path="/instances/:processInstanceId">{children}</Route>
      </MemoryRouter>
    </ThemeProvider>
  );
};

describe('Instance', () => {
  beforeAll(() => {
    mockServer.use(
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res(ctx.text(''))
      ),
      rest.get(
        '/api/process-instances/:instanceId/sequence-flows',
        (_, res, ctx) => res(ctx.json(mockSequenceFlows))
      ),
      rest.post('/api/flow-node-instances', (_, res, ctx) =>
        res(ctx.json(processInstancesMock.level1))
      ),
      rest.get(
        '/api/process-instances/:instanceId/flow-node-states',
        (_, rest, ctx) => rest(ctx.json({}))
      ),
      rest.get('/api/process-instances/core-statistics', (_, res, ctx) =>
        res(
          ctx.json({
            running: 821,
            active: 90,
            withIncidents: 731,
          })
        )
      ),
      rest.get('/api/process-instances/:instanceId/variables', (_, res, ctx) =>
        res(
          ctx.json([
            {
              id: '2251799813686037-mwst',
              name: 'newVariable',
              value: '1234',
              scopeId: '2251799813686037',
              processInstanceId: '2251799813686037',
              hasActiveOperation: false,
            },
          ])
        )
      )
    );
  });

  it('should render and set the page title', async () => {
    mockServer.use(
      rest.get('/api/process-instances/:id', (_, res, ctx) =>
        res.once(ctx.json(testData.fetch.onPageLoad.processInstance))
      )
    );
    mockServer.use(
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(''))
      )
    );

    render(<Instance />, {wrapper: Wrapper});
    jest.useFakeTimers();
    await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));
    expect(await screen.findByTestId('diagram')).toBeInTheDocument();
    expect(screen.getByTestId('diagram-panel-body')).toBeInTheDocument();
    expect(screen.getByText('Instance History')).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.getByText('newVariable')).toBeInTheDocument()
    );
    expect(screen.getByText('newVariable')).toBeInTheDocument();
    expect(document.title).toBe(
      PAGE_TITLE.INSTANCE(
        testData.fetch.onPageLoad.processInstance.id,
        getProcessName(testData.fetch.onPageLoad.processInstance)
      )
    );

    jest.clearAllTimers();
    jest.useRealTimers();
  });
});
