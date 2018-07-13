import React from 'react';
import {shallow} from 'enzyme';

import {mockResolvedAsyncFn, flushPromises} from 'modules/testUtils';

import WrappedInstances from './Instances';
import {DEFAULT_FILTER} from 'modules/constants';
import * as api from 'modules/api/instances/instances';
import Filters from './Filters';
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
      const FiltersNode = node.find(Filters);

      // then
      expect(node.find(ListView)).toHaveLength(1);
      expect(FiltersNode).toHaveLength(1);
      expect(FiltersNode.prop('filter')).toEqual(node.state('filter'));
      expect(FiltersNode.prop('onFilterChange')).toBe(
        node.instance().handleFilterChange
      );
      expect(FiltersNode.prop('resetFilter')).toBe(node.instance().resetFilter);
    });

    it('should pass to the Header the filterCount', async () => {
      const node = shallow(InstancesWithRunningFilter);

      await flushPromises();
      node.update();

      expect(node.find(Header).props().filters).toBe(Count);
    });
  });

  describe('resetFilter', () => {
    it('should reset filter to the default value', () => {
      // given
      const storeStateLocallyMock = jest.fn();
      const node = shallow(
        <Instances
          storeStateLocally={storeStateLocallyMock}
          location={{search: '?filter={"active": false, "incidents": true}'}}
          getStateLocally={() => {
            return {filterCount: 0};
          }}
          history={{push: () => {}}}
        />
      );
      const setFilterInURLlSpy = jest.spyOn(node.instance(), 'setFilterInURL');

      // when
      node.instance().resetFilter();

      // then
      expect(setFilterInURLlSpy).toHaveBeenCalledWith(DEFAULT_FILTER);
      expect(storeStateLocallyMock).toHaveBeenCalledWith({
        filter: DEFAULT_FILTER
      });
    });
  });
});
