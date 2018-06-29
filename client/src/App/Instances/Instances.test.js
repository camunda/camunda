import React from 'react';
import {shallow} from 'enzyme';

import {mockResolvedAsyncFn, flushPromises} from 'modules/testUtils';

import WrappedInstances from './Instances';
import {DEFAULT_FILTER} from 'modules/constants/filter';
import * as api from './api';
import Filter from './Filter';
import ListView from './ListView';
import Header from '../Header';

const Instances = WrappedInstances.WrappedComponent;

const InstancesWithRunningFilter = (
  <Instances
    location={{search: '?filter={"active": false, "incidents": true}'}}
    getStateLocally={() => {
      return {filterCount: 0};
    }}
    storeStateLocally={() => {}}
    history={{push: () => {}}}
  />
);

const InstancesWithInvalidRunningFilter = (
  <Instances
    location={{search: '?filter={"active": fallse, "incidents": tsrue}'}}
    getStateLocally={() => {
      return {filterCount: 0};
    }}
    storeStateLocally={() => {}}
    history={{push: () => {}}}
  />
);

const InstancesWithoutFilter = (
  <Instances
    location={{search: ''}}
    getStateLocally={() => {
      return {filterCount: 0};
    }}
    storeStateLocally={() => {}}
    history={{push: () => {}}}
  />
);

function getRandomInt(max) {
  return Math.floor(Math.random() * Math.floor(max));
}

const Count = getRandomInt(20);

// mock api
api.fetchWorkflowInstancesCount = mockResolvedAsyncFn(Count);
api.fetchWorkflowInstances = mockResolvedAsyncFn([]);

describe('Instances', () => {
  beforeEach(() => {
    api.fetchWorkflowInstancesCount.mockClear();
    api.fetchWorkflowInstances.mockClear();
  });

  describe('filter on initial render', () => {
    it('should initially render without filters selected', () => {
      const count = getRandomInt(20);
      const node = new Instances({
        getStateLocally: () => {
          return {filterCount: count, selections: [[]]};
        }
      });

      expect(node.state.filter).toEqual({});
      expect(node.state.filterCount).toBe(count);
    });
  });

  describe('reading filters from url', () => {
    it('should render the Filter component with provided filters in url', async () => {
      // given
      const node = shallow(InstancesWithRunningFilter);

      // then
      expect(node.state('filter').active).toBe(false);
      expect(node.state('filter').incidents).toBe(true);
    });

    it('should render the Filter with default filter selection when no ?filter=', () => {
      const node = shallow(InstancesWithoutFilter);

      expect(node.state('filter')).toEqual(DEFAULT_FILTER);
    });

    it('should apply default filter selection for an invalid query', () => {
      const node = shallow(InstancesWithInvalidRunningFilter);

      expect(node.state('filter')).toEqual(DEFAULT_FILTER);
    });
  });

  describe('rendering children that receive filter data', () => {
    it('should render the Filter and ListView when filter is in url ', () => {
      // given
      const node = shallow(InstancesWithRunningFilter);
      const FilterNodes = node.find(Filter);
      const RunningFilter = FilterNodes.get(0);
      const ListViewNode = node.find(ListView);

      // then
      expect(FilterNodes).not.toHaveLength(0);
      expect(ListViewNode).toHaveLength(1);

      //check props
      expect(RunningFilter.props.filter.active).toBe(false);
      expect(RunningFilter.props.filter.incidents).toBe(true);
      expect(RunningFilter.props.type).toBe('running');
      expect(RunningFilter.props.onChange).toEqual(
        node.instance().handleFilterChange
      );

      expect(ListViewNode.prop('filter').active).toBe(false);
      expect(ListViewNode.prop('filter').incidents).toBe(true);
    });

    it('should pass to the Header the filterCount', async () => {
      const node = shallow(InstancesWithRunningFilter);

      await flushPromises();
      node.update();

      expect(node.find(Header).props().filters).toBe(Count);
    });
  });
});
