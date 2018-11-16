import React from 'react';
import {shallow} from 'enzyme';

import {PAGE_TITLE} from 'modules/constants';
import {mockResolvedAsyncFn, flushPromises} from 'modules/testUtils';

import Dashboard from './Dashboard';
import MetricPanel from './MetricPanel';
import Header from '../Header';
import IncidentsByWorkflow from './IncidentsByWorkflow';
import * as Styled from './styled';

import * as api from 'modules/api/instances/instances';
import * as apiIncidents from 'modules/api/incidents/incidents';

const mockIncidentsByWorkflow = [
  {
    bpmnProcessId: 'loanProcess',
    workflowName: null,
    instancesWithActiveIncidentsCount: 16,
    activeInstancesCount: 122,
    workflows: [
      {
        workflowId: '3',
        version: 1,
        name: null,
        errorMessage: null,
        instancesWithActiveIncidentsCount: 16,
        activeInstancesCount: 122
      }
    ]
  }
];
api.fetchWorkflowInstancesCount = mockResolvedAsyncFn(123);
apiIncidents.fetchIncidentsByWorkflow = mockResolvedAsyncFn(
  mockIncidentsByWorkflow
);

describe('Dashboard', () => {
  let node;

  beforeEach(() => {
    node = shallow(<Dashboard />);
  });

  it('should set proper page title', () => {
    expect(document.title).toBe(PAGE_TITLE.DASHBOARD);
  });

  it('should render transparent heading', () => {
    expect(node.contains('Camunda Operate Dashboard')).toBe(true);
  });

  it('should render Header component', () => {
    // given
    const mockState = {counts: {running: 1, incidents: 2, active: 1}};
    node.setState(mockState);
    const headerNode = node.find(Header);

    // then
    expect(headerNode).toHaveLength(1);
    expect(headerNode.prop('active')).toBe('dashboard');
    expect(headerNode.prop('runningInstancesCount')).toBe(
      mockState.counts.running
    );
    expect(headerNode.prop('incidentsCount')).toBe(mockState.counts.incidents);
  });

  describe('MetricsPanel', () => {
    it('should render three MetricTile components', async () => {
      expect(node.find(MetricPanel).children().length).toBe(3);
    });
    it('should render MetricPanel component', () => {
      expect(node.find(MetricPanel)).toHaveLength(1);
    });
    it('it should request instance counts ', async () => {
      // when data fetched
      await flushPromises();
      node.update();

      expect(api.fetchWorkflowInstancesCount).toHaveBeenCalled();
    });
  });

  describe('Incidents by Workflow', () => {
    it('should display the Incidents by Workflow box', () => {
      const IncidentsByWorkflow = node.find(
        '[data-test="incidents-byWorkflow"]'
      );

      expect(IncidentsByWorkflow.length).toBe(1);
      expect(IncidentsByWorkflow.find(Styled.TileTitle).text()).toBe(
        'Incidents by Workflow'
      );
    });

    it('should fetch the incidents by workflow', async () => {
      // when data fetched
      await flushPromises();
      node.update();

      expect(apiIncidents.fetchIncidentsByWorkflow).toHaveBeenCalled();
    });

    it('should pass the incidents by workflow to IncidentsByWorkflow', async () => {
      // when data fetched
      await flushPromises();
      node.update();

      expect(node.find(IncidentsByWorkflow).props().incidents).toBe(
        mockIncidentsByWorkflow
      );
    });
  });
});
