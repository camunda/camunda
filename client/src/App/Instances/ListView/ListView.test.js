import React from 'react';
import {shallow} from 'enzyme';

import * as api from 'modules/api/instances/instances';
import {mockResolvedAsyncFn, flushPromises} from 'modules/testUtils';
import {parseFilterForRequest} from 'modules/utils/filter';
import {SORT_ORDER, DEFAULT_SORTING} from 'modules/constants';

import ListView from './ListView';
import List from './List';
import ListFooter from './ListFooter';
import {DEFAULT_FILTER} from 'modules/constants';

const selection = {
  list: new Set(),
  isBlacklist: false
};

const defaultFilter = {DEFAULT_FILTER};
const filter = {
  active: true,
  completed: true,
  workflowIds: ['6'],
  errorMessage: '     lorem ipsum   ',
  ids: `1
  2
  3`,
  startDate: '08.10.2018',
  endDate: '-',
  activityId: '4'
};
const filterCount = 27;
const selections = [];
const successResponse = {totalCount: 123, workflowInstances: [{id: 1}]};

api.fetchWorkflowInstances = mockResolvedAsyncFn(successResponse);
const onUpdateSelection = jest.fn();
const onAddToSpecificSelection = jest.fn();
const onAddToOpenSelection = jest.fn();
const onAddNewSelection = jest.fn();
const onFirstElementChange = jest.fn();

const Component = (
  <ListView
    selection={selection}
    filter={defaultFilter}
    filterCount={filterCount}
    selections={selections}
    openSelection={0}
    onUpdateSelection={onUpdateSelection}
    onAddToSpecificSelection={onAddToSpecificSelection}
    onAddToOpenSelection={onAddToOpenSelection}
    onAddNewSelection={onAddNewSelection}
    onFirstElementChange={onFirstElementChange}
  />
);
describe('ListView', () => {
  beforeEach(() => {
    api.fetchWorkflowInstances.mockClear();
  });

  it('should have initially default state', () => {
    // given
    const node = shallow(Component);

    // then
    expect(node.state().firstElement).toBe(0);
    expect(node.state().instances).toEqual([]);
    expect(node.state().entriesPerPage).toBe(0);
    expect(node.state().sorting).toBe(DEFAULT_SORTING);
    expect(node.state().isDataLoaded).toBe(false);
  });

  it('should reset the page if the filter changes', () => {
    // given
    const node = shallow(Component);

    node.setState({firstElement: 10});
    node.setProps({filter: {prop: 1}});

    expect(node.state().firstElement).toBe(0);
  });

  describe('loadData', () => {
    it('should fetch data on mount if filter is not empty', async () => {
      // given filter is not empty
      const node = shallow(Component);

      // when
      await flushPromises();
      node.update();

      expect(api.fetchWorkflowInstances).toHaveBeenCalled();
      expect(node.state().isDataLoaded).toBe(true);
    });

    it('should not be called when component mounts and filter is empty', () => {
      // given
      shallow(
        <ListView
          selection={selection}
          filter={{}}
          filterCount={filterCount}
          onUpdateSelection={jest.fn()}
          onAddToOpenSelection={jest.fn()}
          onAddNewSelection={jest.fn()}
          onAddToSpecificSelection={jest.fn()}
        />
      );

      // then
      expect(api.fetchWorkflowInstances).not.toHaveBeenCalled();
    });

    it('should fetch data with cleaned filter values', async () => {
      // given
      const node = shallow(
        <ListView
          selection={selection}
          filter={filter}
          filterCount={filterCount}
          onUpdateSelection={onUpdateSelection}
          onAddToOpenSelection={onAddToOpenSelection}
          onAddNewSelection={onAddNewSelection}
          onAddToSpecificSelection={onAddToSpecificSelection}
        />
      );

      // when
      await flushPromises();
      node.update();

      const queries = api.fetchWorkflowInstances.mock.calls[0][0].queries[0];

      // then
      expect(api.fetchWorkflowInstances).toHaveBeenCalledTimes(1);
      expect(queries.running).toBe(true);
      expect(queries.active).toBe(true);
      expect(queries.finished).toBe(true);
      expect(queries.completed).toBe(true);
      expect(queries.workflowIds).toEqual(['6']);
      expect(queries.errorMessage).toEqual('lorem ipsum');
      expect(queries.ids).toEqual(['1', '2', '3']);
      expect(queries.startDateBefore).toContain('2018-08-11T00:00:00.000');
      expect(queries.startDateAfter).toContain('2018-08-10T00:00:00.000');
      expect(queries.startDate).not.toBeDefined();
      expect(queries.endDate).not.toBeDefined();
      expect(queries.endDateBefore).not.toBeDefined();
      expect(queries.endDateAfter).not.toBeDefined();
      // we never pass workflow and version, only activityId to ListView
      expect(queries.workflow).not.toBeDefined();
      expect(queries.version).not.toBeDefined();
      expect(queries.activityId).toContain('4');
    });

    it('should load data if the filter changed', () => {
      // given
      const node = shallow(Component);

      // when
      node.setProps({filter: {foo: 'bar'}});

      // then
      expect(api.fetchWorkflowInstances).toHaveBeenCalled();
    });

    it('should load data if the current page changes', async () => {
      // given
      const node = shallow(Component);

      // when data fetched
      await flushPromises();
      node.update();

      node.setState({firstElement: 10});

      // then
      expect(api.fetchWorkflowInstances).toHaveBeenCalledTimes(2);
      expect(api.fetchWorkflowInstances.mock.calls[1][0].firstResult).toBe(10);
    });

    it('should call api.fetchWorkflowInstances with right data', async () => {
      // given
      const node = shallow(Component);

      // when data fetched
      await flushPromises();
      node.update();

      // then
      expect(api.fetchWorkflowInstances).toBeCalledWith({
        queries: [{...parseFilterForRequest(node.prop('filter'))}],
        sorting: node.state('sorting'),
        firstResult: 0,
        maxResults: 50
      });
      expect(node.state('instances')).toEqual(
        successResponse.workflowInstances
      );
    });
  });

  describe('display instances List', () => {
    it('should not contain a List if the instances are not loaded', () => {
      // given
      const node = shallow(Component);

      // then
      expect(node.find(List)).not.toExist();
      expect(
        node.find('[data-test="empty-message-instances-list"]')
      ).not.toExist();
    });

    it('should not contain a Footer when list is empty', () => {
      // given
      const node = shallow(Component);

      // then
      expect(node.find(ListFooter)).not.toExist();
    });

    it('should display the list and footer after the data is loaded', async () => {
      // given
      const node = shallow(Component);

      // when data fetched
      await flushPromises();
      node.update();

      // then
      expect(node.find(List)).toExist();
      expect(node.find(ListFooter)).toExist();
    });

    it('should pass properties to the Instances List', async () => {
      // given
      const node = shallow(Component);

      // when data fetched
      await flushPromises();
      node.update();

      const list = node.find(List);

      // then
      expect(list.prop('data')).toEqual([{id: 1}]);
      expect(list.prop('selection')).toBe(selection);
      expect(list.prop('filterCount')).toBe(filterCount);
      expect(list.prop('onUpdateSelection')).toBe(onUpdateSelection);
    });

    it('should pass the onUpdateSelection prop to the instances list ', async () => {
      // given
      const node = shallow(Component);

      // when data fetched
      await flushPromises();
      node.update();

      const onUpdateSelection = node.find(List).prop('onUpdateSelection');

      // then
      expect(onUpdateSelection).toBe(onUpdateSelection);
    });

    it('should pass a method to the footer to change the firstElement', async () => {
      // given
      const node = shallow(Component);

      // when data fetched
      await flushPromises();
      node.update();

      node.setState({firstElement: 8});

      const changeFirstElement = node
        .find(ListFooter)
        .prop('onFirstElementChange');

      // then
      expect(changeFirstElement).toBeDefined();
      changeFirstElement(87);
      expect(node.state('firstElement')).toBe(87);
    });

    it('should pass a method to the instances list to update the entries per page', async () => {
      // given
      const node = shallow(Component);

      // when data fetched
      await flushPromises();
      node.update();

      node.setState({entriesPerPage: 8});
      const changeEntriesPerPage = node
        .find(List)
        .prop('onEntriesPerPageChange');

      // then
      expect(changeEntriesPerPage).toBeDefined();
      changeEntriesPerPage(87);
      expect(node.state('entriesPerPage')).toBe(87);
    });

    it('should display a message for empty list when filter has no state', async () => {
      // given
      const emptyFilterNode = shallow(
        <ListView
          selection={selection}
          filter={{}}
          filterCount={filterCount}
          selections={selections}
          openSelection={0}
          onUpdateSelection={onUpdateSelection}
          onAddToSpecificSelection={onAddToSpecificSelection}
          onAddToOpenSelection={onAddToOpenSelection}
          onAddNewSelection={onAddNewSelection}
          onFirstElementChange={onFirstElementChange}
        />
      );

      // when data fetched
      await flushPromises();
      emptyFilterNode.update();

      // force empty instances list
      emptyFilterNode.setState({instances: [], isDataLoaded: true});
      emptyFilterNode.update();

      // then
      expect(
        emptyFilterNode.find('[data-test="empty-message-instances-list"]')
      ).toMatchSnapshot();
    });

    it('should display a empty list message when filter has at least one state', async () => {
      // given
      const withStateFilterNode = shallow(
        <ListView
          selection={selection}
          filter={{active: true}}
          filterCount={filterCount}
          selections={selections}
          openSelection={0}
          onUpdateSelection={onUpdateSelection}
          onAddToSpecificSelection={onAddToSpecificSelection}
          onAddToOpenSelection={onAddToOpenSelection}
          onAddNewSelection={onAddNewSelection}
          onFirstElementChange={onFirstElementChange}
        />
      );

      // when data fetched
      await flushPromises();
      withStateFilterNode.update();

      // force empty instances list
      withStateFilterNode.setState({instances: []});
      withStateFilterNode.update();

      // then
      expect(
        withStateFilterNode.find('[data-test="empty-message-instances-list"]')
      ).toMatchSnapshot();
    });
  });

  describe('handleSorting', () => {
    it('should make state sort order asc if key is currently sorted by in desc order', () => {
      // given
      const node = shallow(Component);
      const KEY = 'foo';
      node.setState({sorting: {sortBy: KEY, sortOrder: SORT_ORDER.DESC}});
      node.update();

      // when
      node.instance().handleSorting(KEY);
      node.update();

      // then
      expect(node.state('sorting').sortBy).toBe(KEY);
      expect(node.state('sorting').sortOrder).toBe(SORT_ORDER.ASC);
    });

    it('should make state sort order desc if key is currently sorted by in asc order', () => {
      // given
      const node = shallow(Component);
      const KEY = 'foo';
      node.setState({sorting: {sortBy: KEY, sortOrder: SORT_ORDER.ASC}});
      node.update();

      // when
      node.instance().handleSorting(KEY);
      node.update();

      // then
      expect(node.state('sorting').sortBy).toBe(KEY);
      expect(node.state('sorting').sortOrder).toBe(SORT_ORDER.DESC);
    });

    it('should make state sort order desc if key is not currently sorted by', () => {
      // given
      const node = shallow(Component);

      const KEY = 'foo';
      node.setState({sorting: {sortBy: 'bar', sortOrder: SORT_ORDER.DESC}});
      node.update();

      // when
      node.instance().handleSorting(KEY);
      node.update();

      // then
      expect(node.state('sorting').sortBy).toBe(KEY);
      expect(node.state('sorting').sortOrder).toBe(SORT_ORDER.DESC);
    });
  });
});
