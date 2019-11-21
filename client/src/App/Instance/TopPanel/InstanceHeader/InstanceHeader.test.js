/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

import {createMockDataManager} from 'modules/testHelpers/dataManager';
import {DataManagerProvider} from 'modules/DataManager';

import {createInstance, setProps} from 'modules/testUtils';

import Pane from 'modules/components/SplitPane/Pane';

import StateIcon from 'modules/components/StateIcon';
import {formatDate} from 'modules/utils/date';
import {
  getWorkflowName,
  getInstancesWithActiveOperations
} from 'modules/utils/instance';

import Actions from 'modules/components/Actions';
import Spinner from 'modules/components/Spinner';

import InstanceHeader from './InstanceHeader';
import Skeleton from './Skeleton';

jest.mock('modules/utils/bpmn');

const mockInstance = createInstance();

createMockDataManager();

const mountInstanceHeader = props => {
  return mount(
    <DataManagerProvider>
      <InstanceHeader {...props} />
    </DataManagerProvider>
  );
};

describe('InstanceHeader', () => {
  let root, node;

  it('should subscribe when instance data is available', () => {
    root = mountInstanceHeader();

    node = root.find(InstanceHeader.WrappedComponent);

    expect(Object.keys(node.instance().subscriptions)).toEqual([]);

    setProps(root, InstanceHeader, {instance: mockInstance});

    root.update();

    node = root.find(InstanceHeader.WrappedComponent);
    const {subscriptions} = node.instance();
    expect(Object.keys(subscriptions)).toEqual([
      'OPERATION_APPLIED_INCIDENT_id_1',
      'OPERATION_APPLIED_VARIABLE_id_1'
    ]);
  });

  it('should show skeleton before instance data is available', () => {
    root = mountInstanceHeader();

    node = root.find(InstanceHeader.WrappedComponent);

    expect(node.find(Skeleton)).toExist();

    setProps(root, InstanceHeader, {instance: mockInstance});

    root.update();

    node = root.find(InstanceHeader.WrappedComponent);

    expect(node.find(Skeleton)).not.toExist();
  });

  it('should render', () => {
    root = mountInstanceHeader({
      instance: mockInstance
    });
    // given
    const workflowName = getWorkflowName(mockInstance);
    const instanceState = mockInstance.state;
    const formattedStartDate = formatDate(mockInstance.startDate);
    const formattedEndDate = formatDate(mockInstance.endDate);

    // then
    expect(root.find(Pane.Header)).toHaveLength(1);

    // Pane.Header
    const PaneHeaderNode = root.find(Pane.Header);

    const TableNode = PaneHeaderNode.find('table');
    expect(TableNode.text()).toContain(workflowName);
    expect(TableNode.text()).toContain(mockInstance.id);
    expect(TableNode.text()).toContain(
      `Version ${mockInstance.workflowVersion}`
    );
    expect(TableNode.text()).toContain(formattedStartDate);
    expect(TableNode.text()).toContain(formattedEndDate);

    const StateIconNode = TableNode.find(StateIcon);
    expect(StateIconNode).toHaveLength(1);
    expect(StateIconNode.prop('state')).toBe(instanceState);

    const ActionsNode = PaneHeaderNode.find(Actions);
    expect(ActionsNode).toExist();
    expect(ActionsNode.props().instance).toEqual(mockInstance);

    expect(ActionsNode.props().forceSpinner).toEqual(
      !!getInstancesWithActiveOperations([mockInstance]).length
    );
  });

  describe('operation feedback', () => {
    it('should show spinner based on instance data', () => {
      root = mountInstanceHeader({
        instance: {...mockInstance, hasActiveOperation: false, operations: []}
      });

      let node = root.find(InstanceHeader.WrappedComponent);

      expect(node.find('Spinner')).not.toExist();
      setProps(root, InstanceHeader, {
        instance: {...mockInstance, hasActiveOperation: true}
      });

      root.update();

      node = root.find(InstanceHeader.WrappedComponent);

      expect(node.find('Spinner')).toExist();
    });

    it('should show spinner when operation is published', () => {
      root = mountInstanceHeader();
      node = root.find(InstanceHeader.WrappedComponent);

      expect(root.find(Spinner)).not.toHaveLength(1);

      setProps(root, InstanceHeader, {
        instance: {...mockInstance, hasActiveOperation: false, operations: []}
      });

      root.update();

      const {subscriptions} = node.instance();

      node.instance().props.dataManager.publish({
        subscription: subscriptions['OPERATION_APPLIED_INCIDENT_id_1'],
        state: 'LOADING'
      });

      root.update();

      //then
      expect(root.find(Spinner)).toHaveLength(1);
    });

    it('should show spinner when variable is edited/created', () => {
      root = mountInstanceHeader();
      node = root.find(InstanceHeader.WrappedComponent);

      expect(root.find(Spinner)).not.toHaveLength(1);

      setProps(root, InstanceHeader, {
        instance: {...mockInstance, hasActiveOperation: false, operations: []}
      });

      root.update();

      const {subscriptions} = node.instance();

      node.instance().props.dataManager.publish({
        subscription: subscriptions['OPERATION_APPLIED_VARIABLE_id_1'],
        state: 'LOADING'
      });

      root.update();

      //then
      expect(root.find(Spinner)).toHaveLength(1);
    });
  });
});
