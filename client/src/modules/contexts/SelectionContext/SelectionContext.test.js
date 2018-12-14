import React from 'react';
import {mount} from 'enzyme';

import {mockResolvedAsyncFn, flushPromises} from 'modules/testUtils';
import {DEFAULT_SELECTED_INSTANCES} from 'modules/constants';
import {serializeInstancesMaps} from 'modules/utils/selection/selection';
import * as instancesApi from 'modules/api/instances/instances';
import {FILTER_SELECTION} from 'modules/constants';

import {SelectionProvider, withSelection} from './SelectionContext';

describe('SelectionContext', () => {
  const mockFunctions = {
    getFilterQuery: jest.fn(),
    getStateLocally: jest.fn(() => ({})),
    storeStateLocally: jest.fn()
  };

  beforeEach(() => {
    instancesApi.fetchWorkflowInstancesBySelection = mockResolvedAsyncFn({});
    instancesApi.fetchWorkflowInstancesByIds = mockResolvedAsyncFn({});
    Object.values(mockFunctions).forEach(fun => fun.mockClear());
  });

  const Foo = withSelection(function Foo() {
    return <div>foo</div>;
  });

  function createNode(customProps = {}) {
    return mount(
      <SelectionProvider {...mockFunctions} {...customProps}>
        <Foo />
      </SelectionProvider>
    );
  }

  it('should add properties to the wrapped component', async () => {
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
    instancesApi.fetchWorkflowInstancesByIds = mockResolvedAsyncFn({
      workflowInstances: [
        {id: 'key1', value: 'newValue1'},
        {id: 'key2', value: 'newValue2'},
        {id: 'key3', value: 'newValue3'}
      ],
      totalCount: 2
    });
    const node = createNode({getStateLocally: mockGetStateLocally});

    // when
    await flushPromises();
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
    const node = createNode({filter: FILTER_SELECTION.running});
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
      const node = createNode();
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
      const node = createNode();
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
    it('should create a new selection', async () => {
      // given
      const selectedInstances = {all: false, ids: ['foo1', 'foo2']};
      const filterQuery = FILTER_SELECTION.incidents;
      instancesApi.fetchWorkflowInstancesByIds = instancesApi.fetchWorkflowInstancesBySelection = mockResolvedAsyncFn(
        {
          workflowInstances: [
            {id: 'foo1', value: 'foo1Value'},
            {id: 'foo2', value: 'foo2Value'}
          ],
          totalCount: 2
        }
      );
      const wrapper = createNode({getFilterQuery: () => filterQuery});
      const node = wrapper.find('BasicSelectionProvider');
      await flushPromises();
      node.instance().handleSelectedInstancesUpdate(selectedInstances);
      wrapper.update();
      const expectedSelectionQueries = [
        {...filterQuery, ids: selectedInstances.ids}
      ];
      const expectedSelection = {
        selectionId: 1,
        instancesMap: new Map([
          ['foo2', {id: 'foo2', value: 'foo2Value'}],
          ['foo1', {id: 'foo1', value: 'foo1Value'}]
        ]),
        queries: expectedSelectionQueries,
        totalCount: 2
      };

      // when
      node.instance().handleAddNewSelection();
      await flushPromises();
      node.update();

      // then

      // (1) backend is called with the right queries
      expect(instancesApi.fetchWorkflowInstancesBySelection).toBeCalled();
      expect(
        instancesApi.fetchWorkflowInstancesBySelection.mock.calls[0][0].queries
      ).toMatchObject(expectedSelectionQueries);

      // (2) state changes with new selection related data
      expect(node.state('selections')[0]).toEqual(expectedSelection);
      expect(node.state('rollingSelectionIndex')).toBe(1);
      expect(node.state('instancesInSelectionsCount')).toBe(2);
      expect(node.state('selectionCount')).toBe(1);
      expect(node.state('openSelection')).toBe(1);
      expect(node.state('selectedInstances')).toEqual(
        DEFAULT_SELECTED_INSTANCES
      );

      // (3) localStorage changes with new selection related data
      const storeStateCall = mockFunctions.storeStateLocally.mock.calls[0][0];
      expect(storeStateCall.selections).toEqual(
        serializeInstancesMaps([expectedSelection])
      );
      expect(storeStateCall.rollingSelectionIndex).toBe(1);
      expect(storeStateCall.instancesInSelectionsCount).toBe(2);
      expect(storeStateCall.selectionCount).toBe(1);
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
      const filterQuery = FILTER_SELECTION.incidents;
      const mockResponse = {
        workflowInstances: [
          {id: 'key1', value: 'value1'},
          {id: 'key2', value: 'value2'},
          {id: 'foo1', value: 'foo1Value'},
          {id: 'foo2', value: 'foo2Value'}
        ],
        totalCount: 4
      };
      instancesApi.fetchWorkflowInstancesByIds = mockResolvedAsyncFn(
        mockResponse
      );
      instancesApi.fetchWorkflowInstancesBySelection = mockResolvedAsyncFn(
        mockResponse
      );
      const wrapper = createNode({
        getFilterQuery: () => filterQuery,
        getStateLocally: () => mockLocalStorage
      });
      const node = wrapper.find('BasicSelectionProvider');
      await flushPromises();
      node.instance().handleSelectedInstancesUpdate(selectedInstances);
      wrapper.update();
      const expectedSelectionQueries = [
        {...filterQuery, ids: selectedInstances.ids},
        {ids: ['key1', 'key2']}
      ];
      const expectedSelection = {
        selectionId: 2,
        instancesMap: new Map([
          ['foo2', {id: 'foo2', value: 'foo2Value'}],
          ['foo1', {id: 'foo1', value: 'foo1Value'}],
          ['key2', {id: 'key2', value: 'value2'}],
          ['key1', {id: 'key1', value: 'value1'}]
        ]),
        queries: expectedSelectionQueries,
        totalCount: 4
      };

      // when
      node.instance().handleAddToSelectionById(2);
      await flushPromises();
      node.update();

      // then
      // (1) backend is called with the right queries
      expect(instancesApi.fetchWorkflowInstancesBySelection).toBeCalled();
      expect(
        instancesApi.fetchWorkflowInstancesBySelection.mock.calls[0][0].queries
      ).toMatchObject(expectedSelectionQueries);

      // (2) state changes with new selection related data
      expect(node.state('selections')[0]).toEqual(expectedSelection);
      expect(node.state('instancesInSelectionsCount')).toBe(4);
      expect(node.state('selectedInstances')).toEqual(
        DEFAULT_SELECTED_INSTANCES
      );
      expect(node.state('openSelection')).toBe(2);

      // (3) localStorage changes with new selection related data
      const storeStateCall = mockFunctions.storeStateLocally.mock.calls[0][0];
      expect(storeStateCall.selections).toEqual(
        serializeInstancesMaps([expectedSelection])
      );
      expect(storeStateCall.instancesInSelectionsCount).toBe(4);
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
      const filterQuery = FILTER_SELECTION.incidents;
      const mockResponse = {
        workflowInstances: [
          {id: 'key1', value: 'value1'},
          {id: 'key2', value: 'value2'},
          {id: 'foo1', value: 'foo1Value'},
          {id: 'foo2', value: 'foo2Value'}
        ],
        totalCount: 4
      };
      instancesApi.fetchWorkflowInstancesByIds = mockResolvedAsyncFn(
        mockResponse
      );
      instancesApi.fetchWorkflowInstancesBySelection = mockResolvedAsyncFn(
        mockResponse
      );
      const wrapper = createNode({
        getFilterQuery: () => filterQuery,
        getStateLocally: () => mockLocalStorage
      });
      const node = wrapper.find('BasicSelectionProvider');
      await flushPromises();
      node.instance().handleSelectedInstancesUpdate(selection);
      const openSelectionId = 2;
      node.instance().handleToggleSelection(openSelectionId);
      wrapper.update();
      const handleAddToSelectionByIdSpy = jest.spyOn(
        node.instance(),
        'handleAddToSelectionById'
      );

      // when
      node.instance().handleAddToSelectionById(2);
      await flushPromises();
      node.update();

      // then
      expect(handleAddToSelectionByIdSpy).toBeCalledWith(openSelectionId);
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
      const filterQuery = FILTER_SELECTION.incidents;
      const mockResponse = {
        workflowInstances: [
          {id: 'key1', value: 'value1'},
          {id: 'key2', value: 'value2'},
          {id: 'foo1', value: 'foo1Value'},
          {id: 'foo2', value: 'foo2Value'}
        ],
        totalCount: 4
      };
      instancesApi.fetchWorkflowInstancesByIds = mockResolvedAsyncFn(
        mockResponse
      );
      instancesApi.fetchWorkflowInstancesBySelection = mockResolvedAsyncFn(
        mockResponse
      );
      const wrapper = createNode({
        getFilterQuery: () => filterQuery,
        getStateLocally: () => mockLocalStorage
      });
      const node = wrapper.find('BasicSelectionProvider');
      await flushPromises();

      // when
      node.instance().handleToggleSelection(2);
      wrapper.update();

      // then
      expect(node.state('openSelection')).toBe(2);
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
      const filterQuery = FILTER_SELECTION.incidents;
      const mockResponse = {
        workflowInstances: [
          {id: 'key1', value: 'value1'},
          {id: 'key2', value: 'value2'}
        ],
        totalCount: 2
      };
      instancesApi.fetchWorkflowInstancesByIds = mockResolvedAsyncFn(
        mockResponse
      );
      instancesApi.fetchWorkflowInstancesBySelection = mockResolvedAsyncFn(
        mockResponse
      );
      const wrapper = createNode({
        getFilterQuery: () => filterQuery,
        getStateLocally: () => mockLocalStorage
      });
      const node = wrapper.find('BasicSelectionProvider');
      await flushPromises();

      // when
      node.instance().handleDeleteSelectionById(2);
      wrapper.update();

      // then

      // (1) state should be updated
      expect(node.state('selections')).toEqual([]);
      expect(node.state('instancesInSelectionsCount')).toBe(0);
      expect(node.state('selectionCount')).toEqual(0);

      // (2) localStorage should be updated
      const storeStateCall = mockFunctions.storeStateLocally.mock.calls[0][0];
      expect(storeStateCall.selections).toEqual(serializeInstancesMaps([]));
      expect(storeStateCall.instancesInSelectionsCount).toBe(0);
      expect(storeStateCall.selectionCount).toBe(0);
    });
  });
});
