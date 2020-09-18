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

import {mockSequenceFlows, mockEvents, mockIncidents} from './index.setup';
import PropTypes from 'prop-types';

import SplitPane from 'modules/components/SplitPane';

import {TopPanel} from './index';
import {currentInstance} from 'modules/stores/currentInstance';
import {singleInstanceDiagram} from 'modules/stores/singleInstanceDiagram';
import {rest} from 'msw';
import {mockServer} from 'modules/mockServer';

jest.mock('modules/utils/bpmn');
jest.mock('./InstanceHeader', () => {
  return {
    InstanceHeader: () => {
      return <div />;
    },
  };
});

const Wrapper = ({children}) => {
  return (
    <MemoryRouter initialEntries={['/instances/1']}>
      <Route path="/instances/:id">
        <SplitPane>
          {children}
          <SplitPane.Pane />
        </SplitPane>
      </Route>
    </MemoryRouter>
  );
};

Wrapper.propTypes = {
  children: PropTypes.oneOfType([
    PropTypes.arrayOf(PropTypes.node),
    PropTypes.node,
  ]),
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
      rest.post('/api/events', (_, res, ctx) => res.once(ctx.json(mockEvents)))
    );
  });

  afterEach(() => {
    singleInstanceDiagram.reset();
    currentInstance.reset();
  });

  it('should render spinner while loading', async () => {
    render(<TopPanel />, {wrapper: Wrapper});

    currentInstance.init('active_instance');
    singleInstanceDiagram.fetchWorkflowXml(1);
    expect(screen.getByTestId('spinner')).toBeInTheDocument();
    await waitForElementToBeRemoved(screen.getByTestId('spinner'));
  });

  it('should render incident bar', async () => {
    render(<TopPanel />, {wrapper: Wrapper});

    currentInstance.init('instance_with_incident');
    await singleInstanceDiagram.fetchWorkflowXml(1);
    expect(
      await screen.findByText('There is 1 Incident in Instance 1.')
    ).toBeInTheDocument();
  });
});
