/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {render, screen} from '@testing-library/react';
import {Router, Route} from 'react-router-dom';
import {createMemoryHistory} from 'history';

import {CollapsablePanelProvider} from 'modules/contexts/CollapsablePanelContext';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {Instances} from './index';
import {
  groupedWorkflowsMock,
  mockWorkflowStatistics,
  mockWorkflowXML,
  mockWorkflowInstances,
  operations,
} from 'modules/testUtils';

import {rest} from 'msw';
import {mockServer} from 'modules/mockServer';

jest.mock('modules/utils/bpmn');
jest.mock('modules/api/instances');
jest.mock('modules/api/diagram');

type Props = {
  children?: React.ReactNode;
  initialRoute?: string;
};

const Wrapper = ({children}: Props) => {
  return (
    <ThemeProvider>
      <CollapsablePanelProvider>
        <Router
          history={createMemoryHistory({
            initialEntries: ['/instances'],
          })}
        >
          <Route path="/instances">{children} </Route>
        </Router>
      </CollapsablePanelProvider>
    </ThemeProvider>
  );
};

describe('Instances', () => {
  beforeEach(() => {
    mockServer.use(
      rest.post(
        '/api/workflow-instances?firstResult=:firstResult&maxResults=:maxResults',
        (_, res, ctx) => res.once(ctx.json(mockWorkflowInstances))
      ),
      rest.get('/api/workflows/:workflowId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockWorkflowXML))
      ),
      rest.get('/api/workflows/grouped', (_, res, ctx) =>
        res.once(ctx.json(groupedWorkflowsMock))
      ),
      rest.post('/api/workflow-instances/statistics', (_, res, ctx) =>
        res.once(ctx.json(mockWorkflowStatistics))
      ),
      rest.post('/api/workflow-instances/core-statistics', (_, res, ctx) =>
        res.once(
          ctx.json({
            coreStatistics: {
              running: 821,
              active: 90,
              withIncidents: 731,
            },
          })
        )
      ),
      rest.post('/api/batch-operations', (_, res, ctx) =>
        res.once(ctx.json(operations))
      )
    );
  });

  it('should render title and document title', () => {
    const mockHistory = createMemoryHistory({initialEntries: ['/instances']});
    const mockLocation = {
      search: '?filter={%22active%22:true,%22incidents%22:true}',
    };
    render(<Instances history={mockHistory} location={mockLocation} />, {
      wrapper: Wrapper,
    });

    expect(screen.getByText('Camunda Operate Instances')).toBeInTheDocument();
    expect(document.title).toBe('Camunda Operate: Instances');
  });

  it('should render page components', () => {
    const mockHistory = createMemoryHistory({initialEntries: ['/instances']});
    const mockLocation = {
      search: '?filter={%22active%22:true,%22incidents%22:true}',
    };
    render(<Instances history={mockHistory} location={mockLocation} />, {
      wrapper: Wrapper,
    });

    // diagram panel
    expect(screen.getByRole('heading', {name: 'Workflow'})).toBeInTheDocument();
    expect(
      screen.getByText('There is no Workflow selected')
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        'To see a Diagram, select a Workflow in the Filters panel'
      )
    ).toBeInTheDocument();

    // filters panel
    expect(screen.getByRole('heading', {name: /Filters/})).toBeInTheDocument();

    // instances table
    expect(
      screen.getByRole('heading', {name: 'Instances'})
    ).toBeInTheDocument();

    // operations
    expect(
      screen.getByRole('button', {name: /expand operations/i})
    ).toBeInTheDocument();
  });
});
