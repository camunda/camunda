/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {render, screen} from '@testing-library/react';
import PropTypes from 'prop-types';
import {Router, Route} from 'react-router-dom';
import {createMemoryHistory} from 'history';

import {CollapsablePanelProvider} from 'modules/contexts/CollapsablePanelContext';

import {Instances} from './index';
import {
  groupedWorkflowsMock,
  mockWorkflowStatistics,
  mockWorkflowXML,
  mockWorkflowInstances,
} from 'modules/testUtils';
import {
  fetchWorkflowInstances,
  fetchGroupedWorkflows,
  fetchWorkflowInstancesStatistics,
  fetchWorkflowInstancesByIds,
  fetchWorkflowCoreStatistics,
} from 'modules/api/instances';
import {fetchWorkflowXML} from 'modules/api/diagram';

jest.mock('modules/utils/bpmn');
jest.mock('modules/api/instances');
jest.mock('modules/api/diagram');

const Wrapper = ({children}) => {
  return (
    <CollapsablePanelProvider>
      <Router
        history={createMemoryHistory({
          initialEntries: ['/instances'],
        })}
      >
        <Route path="/instances">{children} </Route>
      </Router>
    </CollapsablePanelProvider>
  );
};

Wrapper.propTypes = {
  children: PropTypes.oneOfType([
    PropTypes.arrayOf(PropTypes.node),
    PropTypes.node,
  ]),
  initialRoute: PropTypes.string,
};

describe('Instances', () => {
  beforeAll(() => {
    fetchWorkflowInstances.mockImplementation(() => mockWorkflowInstances);
    fetchWorkflowInstancesByIds.mockImplementation(() => mockWorkflowInstances);
    fetchGroupedWorkflows.mockImplementation(() => groupedWorkflowsMock);
    fetchWorkflowInstancesStatistics.mockImplementation(
      () => mockWorkflowStatistics
    );
    fetchWorkflowXML.mockImplementation(() => mockWorkflowXML);
    fetchWorkflowCoreStatistics.mockImplementation(() => ({
      coreStatistics: {
        running: 821,
        active: 90,
        withIncidents: 731,
      },
    }));
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
      screen.getByText('There is no Workflow selected.')
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        'To see a diagram, select a Workflow in the Filters panel.'
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
