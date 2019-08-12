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

import {InstancesPollProvider, withPoll} from './InstancesPollContext';

// props mock
const providerPropsMock = {
  onWorkflowInstancesRefresh: jest.fn(),
  onSelectionsRefresh: jest.fn(),
  visibleIdsInListView: ['1', '2', '3', '4', '5'],
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
      <InstancesPollProvider>
        <Foo />
      </InstancesPollProvider>
    );
    const compProps = node.find(FooComp).props();

    expect(compProps.polling.ids).toEqual([]);
    expect(compProps.polling.addIds).toBeDefined();
    expect(compProps.polling.removeIds).toBeDefined();
  });

  it.skip('should start polling when ids are added', async () => {
    // given
    function FooComp() {
      return <div>foo</div>;
    }
    const Foo = withPoll(FooComp);
    const node = mount(
      <InstancesPollProvider {...providerPropsMock}>
        <Foo />
      </InstancesPollProvider>
    );
    const compProps = node.find(FooComp).props();

    // ids are send for polling
    compProps.polling.addIds(['1', '2', '3']);

    node.update();

    // then
    // expect polling to start
    expect(setTimeout).toBeCalledTimes(1);

    jest.runOnlyPendingTimers();
    await flushPromises();
    node.update();
    // expect to fetch the instances for this ids
    expect(api.fetchWorkflowInstancesByIds).toHaveBeenCalledWith([
      '1',
      '2',
      '3'
    ]);
  });

  it('should append new added ids to the existing ones', () => {
    // given
    function FooComp() {
      return <div>foo</div>;
    }
    const Foo = withPoll(FooComp);
    const node = mount(
      <InstancesPollProvider {...providerPropsMock}>
        <Foo />
      </InstancesPollProvider>
    );
    const compProps = node.find(FooComp).props();

    // when
    compProps.polling.addIds(['1', '2', '3']);
    node.update();

    //then
    expect(node.find(InstancesPollProvider).state().ids).toEqual([
      '1',
      '2',
      '3'
    ]);
    compProps.polling.addIds(['4', '3']);

    node.update();
    expect(node.find(InstancesPollProvider).state().ids).toEqual([
      '4',
      '3',
      '1',
      '2'
    ]);
  });

  it('should refetch the instances list from ListView', async () => {
    // given
    function FooComp() {
      return <div>foo</div>;
    }
    const Foo = withPoll(FooComp);
    const node = mount(
      <InstancesPollProvider {...providerPropsMock}>
        <Foo />
      </InstancesPollProvider>
    );
    const compProps = node.find(FooComp).props();

    // when
    compProps.polling.addIds(['1', '2', '3']);

    jest.runOnlyPendingTimers();
    await flushPromises();
    node.update();

    expect(providerPropsMock.onWorkflowInstancesRefresh).toHaveBeenCalled();
    expect(providerPropsMock.onSelectionsRefresh).not.toHaveBeenCalled();
  });

  it('should refetch the instances & ListView instances ', async () => {
    const COMPLETED_ACTION_INSTANCE = createInstance({
      id: '4',
      hasActiveOperation: false,
      operations: [createOperation({state: 'COMPLETED'})]
    });
    api.fetchWorkflowInstancesByIds = jest.fn().mockResolvedValue({
      workflowInstances: [INSTANCE, COMPLETED_ACTION_INSTANCE]
    }); // default

    // given
    function FooComp() {
      return <div>foo</div>;
    }
    const Foo = withPoll(FooComp);
    const node = mount(
      <InstancesPollProvider {...providerPropsMock}>
        <Foo />
      </InstancesPollProvider>
    );
    const compProps = node.find(FooComp).props();

    // when
    compProps.polling.addIds(['4', '5']);

    jest.runOnlyPendingTimers();
    await flushPromises();
    node.update();

    // instances 4 and 5 are present both in list view and selections
    expect(providerPropsMock.onSelectionsRefresh).toHaveBeenCalled();
    expect(providerPropsMock.onWorkflowInstancesRefresh).toHaveBeenCalled();
  });
  it('should referch the instances in selections list', async () => {
    const COMPLETED_ACTION_INSTANCE = createInstance({
      id: '6',
      hasActiveOperation: false,
      operations: [createOperation({state: 'COMPLETED'})]
    });
    api.fetchWorkflowInstancesByIds = jest.fn().mockResolvedValue({
      workflowInstances: [INSTANCE, COMPLETED_ACTION_INSTANCE]
    }); // default

    // given
    function FooComp() {
      return <div>foo</div>;
    }
    const Foo = withPoll(FooComp);
    const node = mount(
      <InstancesPollProvider {...providerPropsMock}>
        <Foo />
      </InstancesPollProvider>
    );
    const compProps = node.find(FooComp).props();

    // when
    compProps.polling.addIds(['6']);

    jest.runOnlyPendingTimers();
    await flushPromises();
    node.update();

    // instance 6 is only present in selections
    expect(providerPropsMock.onSelectionsRefresh).toHaveBeenCalled();
    expect(providerPropsMock.onWorkflowInstancesRefresh).toHaveBeenCalled();
  });

  it('should continue polling until all ids have completed operations', async () => {
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
      <InstancesPollProvider {...providerPropsMock}>
        <Foo />
      </InstancesPollProvider>
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

    // polling will stop as the active operations on instance#2 is completed
    expect(api.fetchWorkflowInstancesByIds).toHaveBeenCalledTimes(2);
    expect(api.fetchWorkflowInstancesByIds.mock.calls[1][0]).toEqual(['2']);
  });

  it('should stop polling when all operations have finished', async () => {
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
      <InstancesPollProvider {...providerPropsMock}>
        <Foo />
      </InstancesPollProvider>
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

  it('should remove ids with operations', async () => {
    // given
    function FooComp() {
      return <div>foo</div>;
    }
    const Foo = withPoll(FooComp);
    const node = mount(
      <InstancesPollProvider {...providerPropsMock}>
        <Foo />
      </InstancesPollProvider>
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
    expect(api.fetchWorkflowInstancesByIds).toHaveBeenCalledWith(['4', '5']);
  });

  it('should clear polling on unmount', () => {
    function FooComp() {
      return <div>foo</div>;
    }
    const Foo = withPoll(FooComp);
    const node = mount(
      <InstancesPollProvider {...providerPropsMock}>
        <Foo />
      </InstancesPollProvider>
    );
    const compProps = node.find(FooComp).props();

    // ids are send for polling
    compProps.polling.addIds(['1', '2', '3']);

    node.update();

    // then
    // expect polling to start
    expect(setTimeout).toBeCalledTimes(1);
    node.unmount();
    jest.runOnlyPendingTimers();

    // expect to fetch the instances for this ids
    expect(api.fetchWorkflowInstancesByIds).not.toHaveBeenCalled();
  });
});
