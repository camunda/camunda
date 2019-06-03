/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';
import {
  mockResolvedAsyncFn,
  flushPromises,
  createCoreStatistics,
  createInstancesByWorkflow,
  createIncidentsByError
} from 'modules/testUtils';

import {PAGE_TITLE} from 'modules/constants';

import * as api from 'modules/api/instances/instances';
import * as apiIncidents from 'modules/api/incidents/incidents';

import Dashboard from './Dashboard';
import Header from '../Header';
import MetricPanel from './MetricPanel';
import InstancesByWorkflow from './InstancesByWorkflow';
import EmptyIncidents from './EmptyIncidents';
import IncidentsByError from './IncidentsByError';

const mockInstancesByWorkflow = {
  data: createInstancesByWorkflow()
};

const mockIncidentsByError = {
  data: createIncidentsByError()
};

const mockWorkflowCoreStatistics = {
  data: createCoreStatistics()
};

const mockApi = (mockData = {}) => {
  const {
    workflowCoreStatistics = mockWorkflowCoreStatistics,
    InstancesByWorkflow = mockInstancesByWorkflow,
    incidentsByError = mockIncidentsByError
  } = mockData;

  api.fetchWorkflowCoreStatistics = mockResolvedAsyncFn(workflowCoreStatistics);
  apiIncidents.fetchInstancesByWorkflow = mockResolvedAsyncFn(
    InstancesByWorkflow
  );
  apiIncidents.fetchIncidentsByError = mockResolvedAsyncFn(incidentsByError);
};

const shallowRenderDashboard = async () => {
  const node = shallow(<Dashboard />);

  await flushPromises();
  await node.update();

  return node;
};

describe('Dashboard', () => {
  afterEach(() => {
    jest.resetAllMocks();
  });

  it('should set proper page title', async () => {
    mockApi();
    await shallowRenderDashboard();

    expect(document.title).toBe(PAGE_TITLE.DASHBOARD);
  });

  it('should render transparent heading', async () => {
    mockApi();
    const node = await shallowRenderDashboard();

    expect(node.contains('Camunda Operate Dashboard')).toBe(true);
  });

  it('should render Header component', async () => {
    mockApi();
    const node = await shallowRenderDashboard();

    expect(node.find(Header)).toExist();
  });

  it('should render MetricPanel component', async () => {
    mockApi();
    const node = await shallowRenderDashboard();

    const MetricPanelNode = node.find(MetricPanel);

    expect(MetricPanelNode).toExist();
    expect(MetricPanelNode.dive().text()).toContain('10 Running Instances');
  });

  describe('Incidents by Workflow', () => {
    it('should display the Incidents by Workflow box', async () => {
      mockApi();
      const node = await shallowRenderDashboard();

      expect(node.text()).toContain('Instances by Workflow');
      expect(node.find(InstancesByWorkflow)).toExist();
    });

    it('should show empty state on fetch error', async () => {
      await mockApi({InstancesByWorkflow: {data: [], error: 'fetchError'}});
      const node = await shallowRenderDashboard();

      const EmptyIncidentsNode = node
        .find('[data-test="instances-byWorkflow"]')
        .find(EmptyIncidents);

      expect(EmptyIncidentsNode).toExist();
      expect(EmptyIncidentsNode.dive().text()).toContain(
        'Instances by Workflow could not be fetched.'
      );
    });

    it('should show empty state when no workflows', async () => {
      await mockApi({InstancesByWorkflow: {data: [], error: null}});
      const node = await shallowRenderDashboard();

      const EmptyIncidentsNode = node
        .find('[data-test="instances-byWorkflow"]')
        .find(EmptyIncidents);

      expect(EmptyIncidentsNode).toExist();
      expect(EmptyIncidentsNode.dive().text()).toBe('There are no Workflows.');
    });
  });

  describe('Incidents by Error', () => {
    it('should display the Incidents by Error box', async () => {
      mockApi();
      const node = await shallowRenderDashboard();

      expect(node.text()).toContain('Incidents by Error');
      expect(node.find(IncidentsByError)).toExist();
    });

    it('should show empty state on fetch error', async () => {
      await mockApi({incidentsByError: {data: [], error: 'fetchError'}});
      const node = await shallowRenderDashboard();

      const EmptyIncidentsNode = node
        .find('[data-test="incidents-byError"]')
        .find(EmptyIncidents);

      expect(EmptyIncidentsNode).toExist();
      expect(EmptyIncidentsNode.dive().text()).toContain(
        'Incidents by Error Message could not be fetched.'
      );
    });

    it('should show empty state when no workflows', async () => {
      await mockApi({incidentsByError: {data: [], error: null}});
      const node = await shallowRenderDashboard();

      const EmptyIncidentsNode = node
        .find('[data-test="incidents-byError"]')
        .find(EmptyIncidents);

      expect(EmptyIncidentsNode).toExist();
      expect(EmptyIncidentsNode.dive().text()).toBe(
        'There are no Instances with Incident.'
      );
    });
  });
});
