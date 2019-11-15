/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

import {createMockDataManager} from 'modules/testHelpers/dataManager';

import {SUBSCRIPTION_TOPIC, LOADING_STATE} from 'modules/constants';

// Conponents
import FlowNodeInstanceLog from './FlowNodeInstanceLog';
import FlowNodeInstancesTree from '../FlowNodeInstancesTree';

import EmptyPanel from 'modules/components/EmptyPanel';
import Skeleton from './Skeleton';

//Test Data
import {mockProps, dataLoaded} from './FlowNodeInstanceLog.setup';

// Providers
import {ThemeProvider} from 'modules/theme';
import {DataManagerProvider} from 'modules/DataManager';

// mock modules
jest.mock('modules/utils/bpmn');
jest.mock('../FlowNodeInstancesTree', () => {
  return function FlowNodeInstancesTree() {
    return <div />;
  };
});

const mountComponent = props => {
  createMockDataManager();
  return mount(
    <ThemeProvider>
      <DataManagerProvider>
        <FlowNodeInstanceLog {...props} />
      </DataManagerProvider>
    </ThemeProvider>
  );
};

describe('FlowNodeInstanceLog', () => {
  let root, node, subscriptions;
  beforeEach(() => {
    root = mountComponent(mockProps);
    node = root.find('FlowNodeInstanceLog');
    subscriptions = node.instance().subscriptions;
  });

  it('should subscribe and unsubscribe on un/mount', () => {
    //given
    const {dataManager} = node.instance().props;

    //then
    expect(dataManager.subscribe).toHaveBeenCalledWith(subscriptions);

    //when
    root.unmount();
    //then
    expect(dataManager.unsubscribe).toHaveBeenCalledWith(subscriptions);
  });

  it('should render Tree when data is available', () => {
    root = mountComponent(dataLoaded);
    node = root.find(FlowNodeInstanceLog);

    expect(node.find(FlowNodeInstancesTree)).toHaveLength(1);
  });

  it('should render skeleton when data is not available', () => {
    expect(node.find(Skeleton));
  });

  it('should render warning when data could not be fetched', () => {
    //given
    const {dataManager} = node.instance().props;
    // when

    dataManager.publish({
      subscription: subscriptions[SUBSCRIPTION_TOPIC.LOAD_INSTANCE_TREE],
      state: LOADING_STATE.LOAD_FAILED
    });
    root.update();
    // then
    expect(
      node.find(EmptyPanel).contains('Activity Instances could not be fetched')
    );
  });
});
