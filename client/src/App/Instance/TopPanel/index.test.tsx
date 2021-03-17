/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {
  render,
  waitForElementToBeRemoved,
  screen,
} from '@testing-library/react';
import {MemoryRouter, Route} from 'react-router-dom';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {mockSequenceFlows, mockIncidents} from './index.setup';
import SplitPane from 'modules/components/SplitPane';
import {TopPanel} from './index';
import {currentInstanceStore} from 'modules/stores/currentInstance';
import {singleInstanceDiagramStore} from 'modules/stores/singleInstanceDiagram';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';

jest.mock('modules/utils/bpmn');
jest.mock('./InstanceHeader', () => {
  return {
    InstanceHeader: () => {
      return <div />;
    },
  };
});

type Props = {
  children?: React.ReactNode;
};

const Wrapper = ({children}: Props) => {
  return (
    <ThemeProvider>
      <MemoryRouter initialEntries={['/instances/1']}>
        <Route path="/instances/:workflowInstanceId">
          <SplitPane>
            {children}
            <SplitPane.Pane />
          </SplitPane>
        </Route>
      </MemoryRouter>
    </ThemeProvider>
  );
};

describe('TopPanel', () => {
  beforeEach(() => {
    mockServer.use(
      rest.get('/api/workflows/:workflowId/xml', (_, res, ctx) =>
        res.once(ctx.text(''))
      ),
      rest.get('/api/workflow-instances/active_instance', (_, res, ctx) =>
        res.once(
          ctx.json({
            id: 'instance_id',
            state: 'ACTIVE',
          })
        )
      ),
      rest.get(
        '/api/workflow-instances/instance_with_incident',
        (_, res, ctx) =>
          res.once(
            ctx.json({
              id: 'instance_id',
              state: 'INCIDENT',
            })
          )
      ),
      rest.get('/api/workflow-instances/:instanceId/incidents', (_, res, ctx) =>
        res.once(ctx.json(mockIncidents))
      ),
      rest.get(
        '/api/workflow-instances/:instanceId/sequence-flows',
        (_, res, ctx) => res.once(ctx.json(mockSequenceFlows))
      ),
      rest.get(
        '/api/workflow-instances/:instanceId/flow-node-states',
        (_, res, ctx) => res.once(ctx.json({}))
      )
    );
  });

  afterEach(() => {
    singleInstanceDiagramStore.reset();
    currentInstanceStore.reset();
  });

  it('should render spinner while loading', async () => {
    render(<TopPanel />, {
      wrapper: Wrapper,
    });

    currentInstanceStore.init('active_instance');
    singleInstanceDiagramStore.fetchWorkflowXml('1');
    expect(screen.getByTestId('spinner')).toBeInTheDocument();
    await waitForElementToBeRemoved(screen.getByTestId('spinner'));
  });

  it('should render incident bar', async () => {
    render(<TopPanel />, {
      wrapper: Wrapper,
    });

    currentInstanceStore.init('instance_with_incident');
    await singleInstanceDiagramStore.fetchWorkflowXml('1');
    expect(
      await screen.findByText('There is 1 Incident in Instance 1')
    ).toBeInTheDocument();
  });

  it('should show an error when a server error occurs', async () => {
    mockServer.use(
      rest.get('/api/workflows/:workflowId/xml', (_, res, ctx) =>
        res.once(ctx.text(''), ctx.status(500))
      )
    );
    render(<TopPanel />, {
      wrapper: Wrapper,
    });

    currentInstanceStore.init('instance_with_incident');
    singleInstanceDiagramStore.fetchWorkflowXml('1');

    expect(
      await screen.findByText('Diagram could not be fetched')
    ).toBeInTheDocument();
  });

  it('should show an error when a network error occurs', async () => {
    mockServer.use(
      rest.get('/api/workflows/:workflowId/xml', (_, res, ctx) =>
        res.networkError('A network error')
      )
    );
    render(<TopPanel />, {
      wrapper: Wrapper,
    });

    currentInstanceStore.init('instance_with_incident');
    singleInstanceDiagramStore.fetchWorkflowXml('1');

    expect(
      await screen.findByText('Diagram could not be fetched')
    ).toBeInTheDocument();
  });
});
