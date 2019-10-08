/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

import {
  mockResolvedAsyncFn,
  flushPromises,
  createInstance,
  createOperation
} from 'modules/testUtils';

import * as api from 'modules/api/instances/instances';

import {DataManager} from 'modules/DataManager/core';
import {DataManagerProvider} from 'modules/DataManager';
import {InstancesPollProvider, withPoll} from './InstancesPollContext';

jest.mock('modules/utils/bpmn');
// props mock
const providerPropsMock = {
  onWorkflowInstancesRefresh: jest.fn(),
  onSelectionsRefresh: jest.fn(),
  visibleIdsInSelections: ['4', '5', '6']
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

DataManager.mockImplementation(() => {
  return {
    publish: jest.fn(({subscription, state, response}) =>
      subscription({state, response})
    ),
    poll: {start: jest.fn()},
    update: jest.fn(),
    subscribe: jest.fn(),
    getWorkflowInstancesByIds: jest.fn(),
    getWorkflowInstancesBySelection: jest.fn(),
    unsubscribe: jest.fn()
  };
});

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

  // TODO: create a way to mock the poll property of the dataManager
  it.skip('should continue polling until all ids have completed operations', async () => {
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

    // ids are send for polling
    compProps.polling.addIds(['1', '2']);

    node.update();

    // then
    // expect polling to start
    expect(setTimeout).toBeCalledTimes(1);

    jest.runOnlyPendingTimers();
    await flushPromises();
    node.update();

    // TODO: How to Mock instances coming back?
    //expect to fetch the instances for this ids
    expect(
      node.find(InstancesPollProvider.WrappedComponent).instance().props
        .dataManager.getWorkflowInstancesByIds
    ).toHaveBeenCalledWith(['1', '2']);

    jest.runOnlyPendingTimers();
    await flushPromises();
    node.update();

    // polling will stop as the active operations on instance#2 is completed
    expect(api.fetchWorkflowInstancesByIds).toHaveBeenCalledTimes(2);
    expect(api.fetchWorkflowInstancesByIds.mock.calls[1][0]).toEqual(['2']);
  });

  // TODO: create a way to mock the poll property of the dataManager
  it.skip('should stop polling when all operations have finished', async () => {
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
      .mockResolvedValueOnce({workflowInstances: [INSTANCE, ACTIVE_INSTANCE]}); // 2nd call

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
    compProps.polling.addIds(['1', '2']);

    node.update();

    // then
    // expect polling to start
    expect(setTimeout).toBeCalledTimes(1);

    jest.runOnlyPendingTimers();
    await flushPromises();
    node.update();

    // expect to fetch the instances for this ids
    expect(api.fetchWorkflowInstancesByIds).toHaveBeenCalledWith(['1', '2']);

    jest.runOnlyPendingTimers();
    await flushPromises();
    node.update();

    // polling will continue as the active operations on instance#2 is not completed
    expect(api.fetchWorkflowInstancesByIds).toHaveBeenCalledTimes(2);
  });

  // TODO: poll timer
  it.skip('should remove ids with operations', async () => {
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
    compProps.polling.addIds(['1', '4', '5']);
    // ids are removed from polling
    compProps.polling.removeIds(['1', '4', '5']);
    // then
    // expect polling to start
    expect(setTimeout).toBeCalledTimes(1);

    jest.runOnlyPendingTimers();
    await flushPromises();
    node.update();
    // expect to fetch the instances for instances left in selections
    expect(
      node.find(InstancesPollProvider.WrappedComponent).instance().props
        .dataManager.getWorkflowInstancesByIds
    ).toHaveBeenCalledWith(['4', '5']);
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
    // jest.runOnlyPendingTimers();

    // expect to fetch the instances for this ids
    expect(props.dataManager.unsubscribe).toHaveBeenCalled();
  });
});
