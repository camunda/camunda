import React from 'react';
import {shallow} from 'enzyme';

import * as api from 'modules/api/instances';
import {mockResolvedAsyncFn} from 'modules/testUtils';
import {parseFilterForRequest} from 'modules/utils/filter';

import ListView from './ListView';
import List from './List';
import ListFooter from './ListFooter';
import {defaultFilterSelection} from './../service';

const selection = {
  list: new Set(),
  isBlacklist: false
};

const filter = {defaultFilterSelection};
const total = 27;

jest.mock('modules/api/instances');
const successResponse = [{id: 1}];
api.fetchWorkflowInstances = mockResolvedAsyncFn(successResponse);

describe('ListView', () => {
  let node;
  let onSelectionUpdate;
  let onAddToSelection;

  beforeEach(() => {
    onSelectionUpdate = jest.fn();
    onAddToSelection = jest.fn();
    node = shallow(
      <ListView
        selection={selection}
        filter={filter}
        instancesInFilter={total}
        onSelectionUpdate={onSelectionUpdate}
        onAddToSelection={onAddToSelection}
      />
    );
    api.fetchWorkflowInstances.mockClear();
  });

  it('should contain an List', () => {
    expect(node.find(List)).toExist();
  });

  it('should contain a Footer', () => {
    expect(node.find(ListFooter)).toExist();
  });

  describe('load data', () => {
    it('should be called when component mounts and filter is not empty', () => {
      // given filter is not empty
      // then
      node.instance().componentDidMount();
      expect(api.fetchWorkflowInstances).toHaveBeenCalled();
    });

    it('should not be called when component mounts and filter is empty', () => {
      // given
      shallow(
        <ListView
          selection={selection}
          filter={{}}
          instancesInFilter={total}
          onSelectionUpdate={onSelectionUpdate}
          onAddToSelection={onAddToSelection}
        />
      );

      // then
      expect(api.fetchWorkflowInstances).not.toHaveBeenCalled();
    });

    it('should load data if the filter changed', () => {
      // when
      node.setProps({filter: {foo: 'bar'}});

      // then
      expect(api.fetchWorkflowInstances).toHaveBeenCalled();
    });

    it('should load data if the current page changes', () => {
      node.setState({firstElement: 10});

      expect(api.fetchWorkflowInstances).toHaveBeenCalled();
      expect(api.fetchWorkflowInstances.mock.calls[0][1]).toBe(10);
    });

    it('should call api.fetchWorkflowInstances with right data', () => {
      // given
      const expectedFilter = {
        ...parseFilterForRequest(node.prop('filter')),
        sortBy: node.state('sortBy')
      };

      // when
      node.instance().loadData();
      node.update();

      // then
      expect(api.fetchWorkflowInstances).toBeCalledWith(
        expectedFilter,
        node.state('firstElement'),
        50
      );
      expect(node.state('instances')).toEqual(successResponse);
    });
  });

  it('should reset the page if the filter changes', () => {
    node.setState({firstElement: 10});
    node.setProps({filter: {prop: 1}});

    expect(node.state().firstElement).toBe(0);
  });

  it('should pass properties to the Instances List', () => {
    const instances = [{id: 1}, {id: 2}, {id: 3}];
    node.setState({instances});

    const list = node.find(List);

    expect(list.prop('data')).toBe(instances);
    expect(list.prop('selection')).toBe(selection);
    expect(list.prop('total')).toBe(total);
    expect(list.prop('onSelectionUpdate')).toBe(onSelectionUpdate);
  });

  it('should pass properties to the Footer', () => {
    node.setState({entriesPerPage: 14, firstElement: 8});
    const footer = node.find(ListFooter);

    expect(footer.prop('total')).toBe(total);
    expect(footer.prop('perPage')).toBe(14);
    expect(footer.prop('firstElement')).toBe(8);
    expect(footer.prop('onAddToSelection')).toBe(onAddToSelection);
  });

  it('should pass a method to the footer to change the firstElement', () => {
    node.setState({firstElement: 8});
    const changeFirstElement = node
      .find(ListFooter)
      .prop('onFirstElementChange');

    expect(changeFirstElement).toBeDefined();
    changeFirstElement(87);

    expect(node.state('firstElement')).toBe(87);
  });

  it('should pass a method to the instances list to update the entries per page', () => {
    node.setState({entriesPerPage: 8});
    const changeEntriesPerPage = node.find(List).prop('onEntriesPerPageChange');

    expect(changeEntriesPerPage).toBeDefined();
    changeEntriesPerPage(87);

    expect(node.state('entriesPerPage')).toBe(87);
  });

  it('should pass the onSelectionUpdate prop to the instances list ', () => {
    const updateSelection = node.find(List).prop('onSelectionUpdate');

    expect(updateSelection).toBe(onSelectionUpdate);
  });
});
