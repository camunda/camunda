/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

import {BrowserRouter as Router} from 'react-router-dom';
import {PAGE_TITLE, LOADING_STATE, SUBSCRIPTION_TOPIC} from 'modules/constants';

import {createMockDataManager} from 'modules/testHelpers/dataManager';

import {
  incidentsByError,
  instancesByWorkflow,
  fetchError,
  emptyData,
  mockDataStore
} from './Dashboard.setup';

import {Dashboard} from './Dashboard';
import MetricPanel from './MetricPanel';
import InstancesByWorkflow from './InstancesByWorkflow';
import IncidentsByError from './IncidentsByError';
import EmptyPanel from 'modules/components/EmptyPanel';

const mountDashboard = () => {
  const dataManager = createMockDataManager();

  const node = mount(
    <Router>
      <Dashboard dataManager={dataManager} dataStore={mockDataStore} />
    </Router>
  );
  return node;
};

const publish = ({node, topic, response}) => {
  const dashboard = node.find(Dashboard);
  const {
    subscriptions,
    props: {dataManager}
  } = dashboard.instance();

  dataManager.publish({
    subscription: subscriptions[topic],
    state: LOADING_STATE.LOADED,
    response
  });
};

describe('Dashboard', () => {
  afterEach(() => {
    jest.resetAllMocks();
  });

  it('should set proper page title', () => {
    // when
    mountDashboard();

    // then
    expect(document.title).toBe(PAGE_TITLE.DASHBOARD);
  });

  it('should render transparent heading', () => {
    // when
    const node = mountDashboard();

    // then
    expect(node.contains('Camunda Operate Dashboard')).toBe(true);
  });

  it('should render MetricPanel component', () => {
    // when
    const node = mountDashboard();
    const MetricPanelNode = node.find(MetricPanel);

    // then
    expect(MetricPanelNode).toExist();
    expect(MetricPanelNode.text()).toContain(
      `${mockDataStore.running} Running Instances`
    );
  });

  describe('Instances by Workflow', () => {
    it('should display the Instances by Workflow box', () => {
      const node = mountDashboard();

      // when
      publish({
        node,
        topic: SUBSCRIPTION_TOPIC.LOAD_INSTANCES_BY_WORKFLOW,
        response: instancesByWorkflow
      });

      node.update();

      // then
      expect(node.text()).toContain('Instances by Workflow');
      expect(node.find(InstancesByWorkflow)).toExist();
    });

    it('should show empty state on fetch error', () => {
      const node = mountDashboard();

      // when
      publish({
        node,
        topic: SUBSCRIPTION_TOPIC.LOAD_INSTANCES_BY_WORKFLOW,
        response: fetchError
      });

      node.update();

      const EmptyPanelNode = node
        .find('[data-test="instances-byWorkflow"]')
        .find(EmptyPanel);

      // then
      expect(EmptyPanelNode).toExist();
      expect(EmptyPanelNode.props().label).toBe(
        'Instances by Workflow could not be fetched.'
      );
    });

    it('should show empty state when no workflows', () => {
      const node = mountDashboard();

      // when
      publish({
        node,
        topic: SUBSCRIPTION_TOPIC.LOAD_INSTANCES_BY_WORKFLOW,
        response: emptyData
      });

      node.update();

      const EmptyPanelNode = node
        .find('[data-test="instances-byWorkflow"]')
        .find(EmptyPanel);

      // then
      expect(EmptyPanelNode).toExist();
      expect(EmptyPanelNode.props().label).toBe('There are no Workflows.');
    });
  });

  describe('Incidents by Error', () => {
    it('should display the Incidents by Error box', () => {
      const node = mountDashboard();

      // when
      publish({
        node,
        topic: SUBSCRIPTION_TOPIC.LOAD_INCIDENTS_BY_ERROR,
        response: incidentsByError
      });

      node.update();

      // then
      expect(node.text()).toContain('Incidents by Error');
      expect(node.find(IncidentsByError)).toExist();
    });

    it('should show empty state on fetch error', () => {
      const node = mountDashboard();

      // when
      publish({
        node,
        topic: SUBSCRIPTION_TOPIC.LOAD_INCIDENTS_BY_ERROR,
        response: fetchError
      });

      node.update();

      const EmptyPanelNode = node
        .find('[data-test="incidents-byError"]')
        .find(EmptyPanel);

      expect(EmptyPanelNode).toExist();
      expect(EmptyPanelNode.text()).toContain(
        'Incidents by Error Message could not be fetched.'
      );
    });

    it('should show empty state when no workflows', () => {
      const node = mountDashboard();

      // when
      publish({
        node,
        topic: SUBSCRIPTION_TOPIC.LOAD_INCIDENTS_BY_ERROR,
        response: emptyData
      });

      node.update();
      const EmptyPanelNode = node
        .find('[data-test="incidents-byError"]')
        .find(EmptyPanel);

      expect(EmptyPanelNode).toExist();
      expect(EmptyPanelNode.text()).toBe(
        'There are no Instances with Incident.'
      );
    });
  });
});
