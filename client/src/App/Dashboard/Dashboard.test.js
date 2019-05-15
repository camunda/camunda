/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {PAGE_TITLE} from 'modules/constants';
import {mockResolvedAsyncFn, flushPromises} from 'modules/testUtils';

import Dashboard from './Dashboard';
import MetricPanel from './MetricPanel';
import Header from '../Header';
import IncidentsByWorkflow from './IncidentsByWorkflow';
import IncidentsByError from './IncidentsByError';
import EmptyIncidents from './EmptyIncidents';
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
        bpmnProcessId: 'loanProcess',
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

const mockIncidentsByError = [
  {
    errorMessage: "JSON path '$.paid' has no result.",
    instancesWithErrorCount: 36,
    workflows: [
      {
        workflowId: '6',
        version: 2,
        name: 'Order process',
        bpmnProcessId: 'orderProcess',
        errorMessage: "JSON path '$.paid' has no result.",
        instancesWithActiveIncidentsCount: 27,
        activeInstancesCount: null
      },
      {
        workflowId: '1',
        version: 1,
        name: 'Order process',
        bpmnProcessId: 'orderProcess',
        errorMessage: "JSON path '$.paid' has no result.",
        instancesWithActiveIncidentsCount: 9,
        activeInstancesCount: null
      }
    ]
  }
];
const dataByWorkflow = {
  data: mockIncidentsByWorkflow
};
const dataByError = {
  data: mockIncidentsByError
};
api.fetchWorkflowInstancesCount = mockResolvedAsyncFn(123);
apiIncidents.fetchIncidentsByWorkflow = mockResolvedAsyncFn(dataByWorkflow);
apiIncidents.fetchIncidentsByError = mockResolvedAsyncFn(dataByError);

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
        'Instances by Workflow'
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

    it('should render a message when the list is empty', async () => {
      const emptyData = {
        data: []
      };
      apiIncidents.fetchIncidentsByWorkflow = mockResolvedAsyncFn(emptyData);

      // when data fetched
      await flushPromises();
      node.update();

      expect(node.find(EmptyIncidents).length).toBe(1);
      expect(node.find(EmptyIncidents).props().label).toBe(
        'There are no Workflows.'
      );
      expect(node.find(EmptyIncidents).props().type).toBe('info');

      expect(node.find(IncidentsByWorkflow).length).toBe(0);

      //reset mockIncidentsByWorkflow
      apiIncidents.fetchIncidentsByWorkflow = mockResolvedAsyncFn(
        dataByWorkflow
      );
    });

    it('should render a message when the list is empty', async () => {
      const errorData = {
        data: [],
        error: 'someError'
      };
      apiIncidents.fetchIncidentsByWorkflow = mockResolvedAsyncFn(errorData);

      // when data fetched
      await flushPromises();
      node.update();

      expect(node.find(EmptyIncidents).length).toBe(1);
      expect(node.find(EmptyIncidents).props().label).toBe(
        'Instances by Workflow could not be fetched.'
      );
      expect(node.find(EmptyIncidents).props().type).toBe('warning');

      expect(node.find(IncidentsByWorkflow).length).toBe(0);

      //reset mockIncidentsByWorkflow
      apiIncidents.fetchIncidentsByWorkflow = mockResolvedAsyncFn(
        dataByWorkflow
      );
    });
  });
  describe('Incidents by Error', () => {
    it('should display the Incidents by Error box', () => {
      const IncidentsByError = node.find('[data-test="incidents-byError"]');

      expect(IncidentsByError.length).toBe(1);
      expect(IncidentsByError.find(Styled.TileTitle).text()).toBe(
        'Incidents by Error Message'
      );
    });

    it('should fetch the incidents by workflow', async () => {
      // when data fetched
      await flushPromises();
      node.update();

      expect(apiIncidents.fetchIncidentsByError).toHaveBeenCalled();
    });

    it('should pass the incidents by workflow to IncidentsByWorkflow', async () => {
      // when data fetched
      await flushPromises();
      node.update();

      expect(node.find(IncidentsByError).props().incidents).toBe(
        mockIncidentsByError
      );
    });

    it('should render a message when the list is empty', async () => {
      const emptyData = {
        data: []
      };
      apiIncidents.fetchIncidentsByError = mockResolvedAsyncFn(emptyData);

      // when data fetched
      await flushPromises();
      node.update();

      expect(node.find(EmptyIncidents).length).toBe(1);
      expect(node.find(EmptyIncidents).props().label).toBe(
        'There are no Workflows.'
      );
      expect(node.find(EmptyIncidents).props().type).toBe('info');

      expect(node.find(IncidentsByError).length).toBe(0);

      //reset mockIncidentsByWorkflow
      apiIncidents.fetchIncidentsByError = mockResolvedAsyncFn(dataByError);
    });

    it('should render a message when the list is empty', async () => {
      const errorData = {
        data: [],
        error: 'someError'
      };
      apiIncidents.fetchIncidentsByError = mockResolvedAsyncFn(errorData);

      // when data fetched
      await flushPromises();
      node.update();

      expect(node.find(EmptyIncidents).length).toBe(1);
      expect(node.find(EmptyIncidents).props().label).toBe(
        'Incidents by Error Message could not be fetched.'
      );
      expect(node.find(EmptyIncidents).props().type).toBe('warning');

      expect(node.find(IncidentsByError).length).toBe(0);

      //reset mockIncidentsByWorkflow
      apiIncidents.fetchIncidentsByError = mockResolvedAsyncFn(dataByError);
    });
  });
});
