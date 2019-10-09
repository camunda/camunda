/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

import {
  mockResolvedAsyncFn,
  createInstance,
  createOperation,
  mockDataManager
} from 'modules/testUtils';

import {SUBSCRIPTION_TOPIC} from 'modules/constants';

import * as api from 'modules/api/instances/instances';

import {DataManager} from 'modules/DataManager/core';
import {DataManagerProvider} from 'modules/DataManager';
import {InstancesPollProvider, withPoll} from './InstancesPollContext';

jest.mock('modules/utils/bpmn');
// props mock
const providerPropsMock = {
  onWorkflowInstancesRefresh: jest.fn(),
  onSelectionsRefresh: jest.fn(),
  visibleIdsInSelections: ['4', '5', '6'],
  filter: {workflow: 'asd', version: '1'}
};

// api mock
const INSTANCE = createInstance({
  id: '1',
  hasActiveOperation: false,
  operations: [createOperation({state: 'FAILED'})]
});
const ACTIVE_INSTANCE = createInstance({
  id: '2',
  hasActiveOperation: true,
  operations: [createOperation({state: 'SENT'})]
});

api.fetchWorkflowInstancesByIds = mockResolvedAsyncFn({
  workflowInstances: [INSTANCE, ACTIVE_INSTANCE]
});

jest.mock('modules/DataManager/core');

DataManager.mockImplementation(mockDataManager);

describe('InstancesPollContext', () => {
  beforeEach(() => {
    jest.useFakeTimers();
    jest.clearAllMocks();
  });

  afterEach(() => {
    jest.clearAllTimers();
  });

  it('should pass the right props to the wrapper component', () => {
    function FooComp() {
      return <div>foo</div>;
    }
    const Foo = withPoll(FooComp);
    const node = mount(
      <DataManagerProvider>
        <InstancesPollProvider>
          <Foo />
        </InstancesPollProvider>
      </DataManagerProvider>
    );
    const compProps = node.find(FooComp).props();

    expect(compProps.polling.active).toEqual(new Set());
    expect(compProps.polling.complete).toEqual(new Set());
    expect(compProps.polling.addIds).toBeDefined();
    expect(compProps.polling.removeIds).toBeDefined();
  });

  it('should start polling when ids are added', async () => {
    // given
    function FooComp() {
      return <div>foo</div>;
    }
    const Foo = withPoll(FooComp);
    const node = mount(
      <DataManagerProvider>
        <InstancesPollProvider {...providerPropsMock}>
          <Foo />
        </InstancesPollProvider>
      </DataManagerProvider>
    );
    const compProps = node.find(FooComp).props();

    // ids are send for polling
    compProps.polling.addIds(['1', '2', '3']);

    node.update();

    // then
    // expect polling to start
    expect(
      node.find(InstancesPollProvider.WrappedComponent).instance().props
        .dataManager.poll.start
    ).toHaveBeenCalled();
  });

  it('should append new added ids to the existing ones', () => {
    // given
    function FooComp() {
      return <div>foo</div>;
    }
    const Foo = withPoll(FooComp);
    const node = mount(
      <DataManagerProvider>
        <InstancesPollProvider {...providerPropsMock}>
          <Foo />
        </InstancesPollProvider>
      </DataManagerProvider>
    );
    const compProps = node.find(FooComp).props();

    // when
    compProps.polling.addIds(['1', '2', '3']);
    node.update();

    //then
    expect(
      node.find(InstancesPollProvider.WrappedComponent).instance().state.active
    ).toEqual(new Set(['1', '2', '3']));
    compProps.polling.addIds(['4', '3']);

    node.update();
    expect(
      node.find(InstancesPollProvider.WrappedComponent).instance().state.active
    ).toEqual(new Set(['4', '3', '1', '2']));
  });

  it('should refetch the instances list from ListView', async () => {
    // given
    function FooComp() {
      return <div>foo</div>;
    }
    const Foo = withPoll(FooComp);
    const node = mount(
      <DataManagerProvider>
        <InstancesPollProvider {...providerPropsMock}>
          <Foo />
        </InstancesPollProvider>
      </DataManagerProvider>
    );
    const compProps = node.find(FooComp).props();

    // when
    compProps.polling.addIds(['1', '2', '3']);

    node.update();

    expect(
      node.find(InstancesPollProvider.WrappedComponent).instance().props
        .dataManager.poll.start
    ).toHaveBeenCalled();

    //TODO: mock waiting for poll to complete
    // jest.runOnlyPendingTimers();
    // await flushPromises();

    // expect(
    //   node.find(InstancesPollProvider.WrappedComponent).instance().props
    //     .dataManager.getWorkflowInstancesByIds
    // ).toHaveBeenCalled();
  });

  it('should poll when there are active instances', async () => {
    const COMPLETED_ACTION_INSTANCE = createInstance({
      id: '2',
      hasActiveOperation: false,
      operations: [createOperation({state: 'COMPLETED'})]
    });
    api.fetchWorkflowInstancesByIds = jest
      .fn()
      .mockResolvedValue({
        workflowInstances: [INSTANCE, COMPLETED_ACTION_INSTANCE]
      }) // default
      .mockResolvedValueOnce({workflowInstances: [INSTANCE, ACTIVE_INSTANCE]}) // 1st call
      .mockResolvedValueOnce({
        workflowInstances: [INSTANCE, COMPLETED_ACTION_INSTANCE]
      }); // 2nd call

    // given
    function FooComp() {
      return <div>foo</div>;
    }
    const Foo = withPoll(FooComp);
    const node = mount(
      <DataManagerProvider>
        <InstancesPollProvider {...providerPropsMock}>
          <Foo />
        </InstancesPollProvider>
      </DataManagerProvider>
    );
    const compProps = node.find(FooComp).props();
    const dataManagerMock = node
      .find(InstancesPollProvider.WrappedComponent)
      .instance().props.dataManager;

    // ids are send for polling
    compProps.polling.addIds(['1', '2']);

    node.update();

    // then
    // expect polling to start
    expect(dataManagerMock.poll.start).toHaveBeenCalled();
    expect(dataManagerMock.getWorkflowInstancesByIds).toHaveBeenCalledWith(
      ['1', '2'],
      SUBSCRIPTION_TOPIC.LOAD_SELECTION_INSTANCES
    );
  });

  it('should update when instances have completed operations', () => {
    const COMPLETED_ACTION_INSTANCE = createInstance({
      id: '2',
      hasActiveOperation: false,
      operations: [createOperation({state: 'COMPLETED'})]
    });

    // given
    function FooComp() {
      return <div>foo</div>;
    }
    const Foo = withPoll(FooComp);
    const node = mount(
      <DataManagerProvider>
        <InstancesPollProvider {...providerPropsMock}>
          <Foo />
        </InstancesPollProvider>
      </DataManagerProvider>
    );

    const dataManagerMock = node
      .find(InstancesPollProvider.WrappedComponent)
      .instance().props.dataManager;

    const subscriptions = node
      .find(InstancesPollProvider.WrappedComponent)
      .instance().subscriptions;

    dataManagerMock.publish({
      subscription: subscriptions['LOAD_SELECTION_INSTANCES'],
      state: 'LOADED',
      response: {
        workflowInstances: [INSTANCE, COMPLETED_ACTION_INSTANCE]
      }
    });

    expect(dataManagerMock.update).toHaveBeenCalled();
  });

  it('should clear polling on unmount', () => {
    function FooComp() {
      return <div>foo</div>;
    }
    const Foo = withPoll(FooComp);
    const node = mount(
      <DataManagerProvider>
        <InstancesPollProvider {...providerPropsMock}>
          <Foo />
        </InstancesPollProvider>
      </DataManagerProvider>
    );
    const compProps = node.find(FooComp).props();

    // ids are send for polling
    compProps.polling.addIds(['1', '2', '3']);

    node.update();

    const props = node.find(InstancesPollProvider.WrappedComponent).instance()
      .props;
    // then
    // expect polling to start
    expect(props.dataManager.poll.start).toBeCalledTimes(1);
    node.unmount();

    // expect to fetch the instances for this ids
    expect(props.dataManager.unsubscribe).toHaveBeenCalled();
  });
});
