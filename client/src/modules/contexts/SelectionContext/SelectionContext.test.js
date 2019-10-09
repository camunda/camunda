/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';
import {DataManagerProvider} from 'modules/DataManager';
import {DataManager} from 'modules/DataManager/core';

import {
  mockResolvedAsyncFn,
  groupedWorkflowsMock,
  mockDataManager
} from 'modules/testUtils';
import {
  DEFAULT_SELECTED_INSTANCES,
  SUBSCRIPTION_TOPIC
} from 'modules/constants';
import {serializeInstancesMaps} from 'modules/utils/selection/selection';
import {formatGroupedWorkflows} from 'modules/utils/instance';
import * as instancesApi from 'modules/api/instances/instances';
import {FILTER_SELECTION, LOADING_STATE} from 'modules/constants';

import {SelectionProvider, withSelection} from './SelectionContext';

const mockProps = {
  getStateLocally: jest.fn(() => ({})),
  storeStateLocally: jest.fn(),
  groupedWorkflows: formatGroupedWorkflows(groupedWorkflowsMock),
  filter: FILTER_SELECTION.incidents
};

jest.mock('modules/utils/bpmn');
jest.mock('modules/DataManager/core');

DataManager.mockImplementation(mockDataManager);

describe('SelectionContext', () => {
  beforeEach(() => {
    instancesApi.fetchWorkflowInstancesBySelection = mockResolvedAsyncFn({});
    instancesApi.fetchWorkflowInstancesByIds = mockResolvedAsyncFn({});
    jest.clearAllMocks();
  });

  const Foo = withSelection(function Foo() {
    return <div>foo</div>;
  });

  function mountNode(customProps = {}) {
    return mount(
      <SelectionProvider {...mockProps} {...customProps}>
        <Foo />
      </SelectionProvider>
    );
  }

  it('should add properties to the wrapped component', async () => {
    const dataManager = new DataManager();
    // given
    const localStorageData = {
      instancesInSelectionsCount: 3,
      rollingSelectionIndex: 2,
      selectionCount: 2,
      selections: serializeInstancesMaps([
        {
          totalCount: 2,
          selectionId: 2,
          instancesMap: new Map([
            ['key1', {id: 'key1', value: 'value1'}],
            ['key2', {id: 'key2', value: 'value2'}]
          ])
        },
        {
          totalCount: 1,
          selectionId: 1,
          instancesMap: [['key3', {id: 'key3', value: 'value3'}]]
        }
      ])
    };
    const mockGetStateLocally = jest.fn(() => localStorageData);

    const node = mount(
      <DataManagerProvider>
        <SelectionProvider
          {...mockProps}
          {...{getStateLocally: mockGetStateLocally}}
        >
          <Foo />
        </SelectionProvider>
      </DataManagerProvider>
    );

    const subscriptions = node
      .find(SelectionProvider.WrappedComponent)
      .instance().subscriptions;

    dataManager.publish({
      subscription: subscriptions['REFRESH_SELECTION'],
      state: LOADING_STATE.LOADED,
      response: {
        workflowInstances: [
          {id: 'key1', value: 'newValue1'},
          {id: 'key2', value: 'newValue2'},
          {id: 'key3', value: 'newValue3'}
        ],
        totalCount: 2
      }
    });

    // when

    node.update();

    // then
    const fooNode = node.find('Foo');
    expect(fooNode.prop('instancesInSelectionsCount')).toBe(
      localStorageData.instancesInSelectionsCount
    );
    expect(fooNode.prop('openSelection')).toBe(null);
    expect(fooNode.prop('rollingSelectionIndex')).toBe(
      localStorageData.rollingSelectionIndex
    );
    expect(fooNode.prop('selectedInstances')).toEqual(
      DEFAULT_SELECTED_INSTANCES
    );
    expect(fooNode.prop('selectionCount')).toBe(
      localStorageData.selectionCount
    );

    const selections = fooNode.prop('selections');
    expect(selections[0].instancesMap.get('key1').value).toBe('newValue1');
    expect(selections[0].instancesMap.get('key2').value).toBe('newValue2');
    expect(selections[1].instancesMap.get('key3').value).toBe('newValue3');
  });

  it('should reset selected instances when filter changes', () => {
    // given
    const dataManager = new DataManager();

    const node = mount(
      <SelectionProvider
        {...mockProps}
        {...{filter: FILTER_SELECTION.running}}
        {...{dataManager}}
      >
        <Foo />
      </SelectionProvider>
    );

    let fooNode = node.find('Foo');
    const {onSelectedInstancesUpdate} = fooNode.props();
    onSelectedInstancesUpdate({all: true});
    node.update();

    // when
    node.setProps({filter: FILTER_SELECTION.incidents});
    node.update();
    fooNode = node.find('Foo');

    // then
    expect(fooNode.prop('selectedInstances')).toBe(DEFAULT_SELECTED_INSTANCES);
  });

  describe('handleSelectedInstancesUpdate', () => {
    it('should update selected instances', () => {
      // given
      const node = mount(
        <DataManagerProvider>
          <SelectionProvider {...mockProps}>
            <Foo />
          </SelectionProvider>
        </DataManagerProvider>
      );
      let fooNode = node.find('Foo');
      const {onSelectedInstancesUpdate} = fooNode.props();

      // when
      onSelectedInstancesUpdate({all: true});
      node.update();
      fooNode = node.find('Foo');

      // then
      expect(fooNode.prop('selectedInstances')).toEqual({all: true});
    });
  });

  describe('handleSelectedInstancesReset', () => {
    it('should reset selected instances', () => {
      // given
      const node = mountNode();
      let fooNode = node.find('Foo');
      const {
        onSelectedInstancesUpdate,
        onSelectedInstancesReset
      } = fooNode.props();
      onSelectedInstancesUpdate({all: true});
      node.update();

      // when
      onSelectedInstancesReset();
      node.update();
      fooNode = node.find('Foo');

      // then
      expect(fooNode.prop('selectedInstances')).toEqual(
        DEFAULT_SELECTED_INSTANCES
      );
    });
  });

  describe('handleAddNewSelection', () => {
    it('should trigger the right data request', () => {
      const selectedInstances = {all: false, ids: ['foo1', 'foo2']};
      const expectedSelectionQueries = [
        {
          ...FILTER_SELECTION.incidents,
          running: true,
          ids: selectedInstances.ids
        }
      ];
      const wrapper = mount(
        <DataManagerProvider>
          <SelectionProvider {...mockProps}>
            <Foo />
          </SelectionProvider>
        </DataManagerProvider>
      );

      const node = wrapper.find(SelectionProvider.WrappedComponent);
      node.instance().handleSelectedInstancesUpdate(selectedInstances);
      node.instance().handleAddNewSelection();

      expect(
        node.instance().props.dataManager.getWorkflowInstancesBySelection
      ).toHaveBeenCalledWith(
        {queries: expectedSelectionQueries},
        SUBSCRIPTION_TOPIC.LOAD_NEW_SELECTION
      );
    });
  });

  describe('handleAddToOpenSelection', () => {
    it('should add selected instances to the currently open selection', async () => {
      // given
      const selection = {all: false, ids: ['foo1', 'foo2']};
      const mockLocalStorage = {
        instancesInSelectionsCount: 2,
        rollingSelectionIndex: 2,
        selectionCount: 1,
        selections: serializeInstancesMaps([
          {
            totalCount: 2,
            selectionId: 2,
            instancesMap: new Map([
              ['key1', {id: 'key1', value: 'value1'}],
              ['key2', {id: 'key2', value: 'value2'}]
            ]),
            queries: [{ids: ['key1', 'key2']}]
          }
        ])
      };

      const wrapper = mount(
        <DataManagerProvider>
          <SelectionProvider
            {...mockProps}
            {...{getStateLocally: () => mockLocalStorage}}
          >
            <Foo />
          </SelectionProvider>
        </DataManagerProvider>
      );

      const node = wrapper.find('BasicSelectionProvider');

      node.instance().handleSelectedInstancesUpdate(selection);
      const openSelectionId = 2;
      node.instance().handleToggleSelection(openSelectionId);
      wrapper.update();

      // when
      node.instance().handleAddToSelectionById(2);

      // then
      expect(
        node.instance().props.dataManager.getWorkflowInstancesBySelection
      ).toHaveBeenCalled();
    });
  });

  describe('handleToggleSelection', () => {
    it('should set open selection to the provided selection id', async () => {
      const mockLocalStorage = {
        instancesInSelectionsCount: 2,
        rollingSelectionIndex: 2,
        selectionCount: 1,
        selections: serializeInstancesMaps([
          {
            totalCount: 2,
            selectionId: 2,
            instancesMap: new Map([
              ['key1', {id: 'key1', value: 'value1'}],
              ['key2', {id: 'key2', value: 'value2'}]
            ]),
            queries: [{ids: ['key1', 'key2']}]
          }
        ])
      };

      const wrapper = mount(
        <DataManagerProvider>
          <SelectionProvider
            {...mockProps}
            {...{getStateLocally: () => mockLocalStorage}}
          >
            <Foo />
          </SelectionProvider>
        </DataManagerProvider>
      );
      const node = wrapper.find('BasicSelectionProvider');

      // when
      node.instance().handleToggleSelection(2);
      wrapper.update();

      // then
      expect(node.instance().state.openSelection).toBe(2);
    });
  });

  describe('handleDeleteSelection', () => {
    it('should delete the target selection from the list of selections', async () => {
      const mockLocalStorage = {
        instancesInSelectionsCount: 2,
        rollingSelectionIndex: 2,
        selectionCount: 1,
        selections: serializeInstancesMaps([
          {
            totalCount: 2,
            selectionId: 2,
            instancesMap: new Map([
              ['key1', {id: 'key1', value: 'value1'}],
              ['key2', {id: 'key2', value: 'value2'}]
            ]),
            queries: [{ids: ['key1', 'key2']}]
          }
        ])
      };

      const wrapper = mount(
        <DataManagerProvider>
          <SelectionProvider
            {...mockProps}
            {...{getStateLocally: () => mockLocalStorage}}
          >
            <Foo />
          </SelectionProvider>
        </DataManagerProvider>
      );

      const node = wrapper.find('BasicSelectionProvider');

      // when
      node.instance().handleDeleteSelectionById(2);
      wrapper.update();

      // then

      // (1) state should be updated
      expect(node.instance().state.selections).toEqual([]);
      expect(node.instance().state.instancesInSelectionsCount).toBe(0);
      expect(node.instance().state.selectionCount).toEqual(0);

      // (2) localStorage should be updated
      const storeStateCall = mockProps.storeStateLocally.mock.calls[0][0];
      expect(storeStateCall.selections).toEqual(serializeInstancesMaps([]));
      expect(storeStateCall.instancesInSelectionsCount).toBe(0);
      expect(storeStateCall.selectionCount).toBe(0);
    });
  });

  describe('handleAddToSelectionById', async () => {
    it('should add selected instances to target selection', async () => {
      // given
      const selectedInstances = {all: false, ids: ['foo1', 'foo2']};
      const mockLocalStorage = {
        instancesInSelectionsCount: 2,
        rollingSelectionIndex: 2,
        selectionCount: 1,
        selections: serializeInstancesMaps([
          {
            totalCount: 2,
            selectionId: 2,
            instancesMap: new Map([
              ['key1', {id: 'key1', value: 'value1'}],
              ['key2', {id: 'key2', value: 'value2'}]
            ]),
            queries: [{ids: ['key1', 'key2']}]
          }
        ])
      };
      const mockResponse = {
        workflowInstances: [
          {id: 'key1', value: 'value1'},
          {id: 'key2', value: 'value2'},
          {id: 'foo1', value: 'foo1Value'},
          {id: 'foo2', value: 'foo2Value'}
        ],
        selectionId: 2,
        totalCount: 4
      };

      const wrapper = mount(
        <DataManagerProvider>
          <SelectionProvider
            {...mockProps}
            {...{getStateLocally: () => mockLocalStorage}}
          >
            <Foo />
          </SelectionProvider>
        </DataManagerProvider>
      );

      const node = wrapper.find('BasicSelectionProvider');

      node.instance().handleSelectedInstancesUpdate(selectedInstances);
      wrapper.update();
      const expectedSelectionQueries = [
        {...mockProps.filter, running: true, ids: selectedInstances.ids},
        {ids: ['key1', 'key2']}
      ];
      const expectedSelection = {
        selectionId: 2,
        instancesMap: new Map([
          ['key1', {id: 'key1', value: 'value1'}],
          ['key2', {id: 'key2', value: 'value2'}],
          ['foo1', {id: 'foo1', value: 'foo1Value'}],
          ['foo2', {id: 'foo2', value: 'foo2Value'}]
        ]),
        queries: expectedSelectionQueries,
        totalCount: 4
      };

      // when
      node.instance().handleAddToSelectionById(2);

      node.update();

      // then
      // (1) backend is called with the right queries
      expect(
        node.instance().props.dataManager.getWorkflowInstancesBySelection
      ).toHaveBeenCalledWith(
        {queries: expectedSelectionQueries},
        SUBSCRIPTION_TOPIC.LOAD_UPDATE_SELECTION,
        2
      );

      // when
      node.instance().addToSelectionById(mockResponse);

      node.update();

      // // (2) state changes with new selection related data
      expect(node.instance().state.selections[0]).toEqual(expectedSelection);
      expect(node.instance().state.instancesInSelectionsCount).toBe(4);
      expect(node.instance().state.selectedInstances).toEqual(
        DEFAULT_SELECTED_INSTANCES
      );
      expect(node.instance().state.openSelection).toBe(2);

      // // (3) localStorage changes with new selection related data
      const storeStateCall = mockProps.storeStateLocally.mock.calls[0][0];
      expect(storeStateCall.selections).toEqual(
        serializeInstancesMaps([expectedSelection])
      );
      expect(storeStateCall.instancesInSelectionsCount).toBe(4);
    });
  });
});
