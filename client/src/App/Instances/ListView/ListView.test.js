import React from 'react';
import {shallow} from 'enzyme';

import {flushPromises, createSelection} from 'modules/testUtils';
import {SORT_ORDER, DEFAULT_SORTING} from 'modules/constants';

import ListView from './ListView';
import List from './List';
import ListFooter from './ListFooter';
import {DEFAULT_FILTER} from 'modules/constants';

const selection = createSelection();

const defaultFilter = {DEFAULT_FILTER};
const filterCount = 27;
const selections = [];

const onUpdateSelection = jest.fn();
const onAddToSpecificSelection = jest.fn();
const onAddToOpenSelection = jest.fn();
const onAddNewSelection = jest.fn();
const onFirstElementChange = jest.fn();

const mockProps = {
  instances: [],
  instancesLoaded: false,
  fetchWorkflowInstances: jest.fn(),
  selection: selection,
  filter: defaultFilter,
  filterCount: filterCount,
  selections: selections,
  openSelection: 0,
  onUpdateSelection: onUpdateSelection,
  onAddToSpecificSelection: onAddToSpecificSelection,
  onAddToOpenSelection: onAddToOpenSelection,
  onAddNewSelection: onAddNewSelection,
  onFirstElementChange: onFirstElementChange,
  onSort: jest.fn(),
  sorting: DEFAULT_SORTING,
  firstElement: 0
};
const mockPropsWithInstances = {
  ...mockProps,
  instances: [{id: 1}],
  instancesLoaded: true
};
const Component = <ListView {...mockProps} />;
const ComponentWithInstances = <ListView {...mockPropsWithInstances} />;

describe('ListView', () => {
  beforeEach(() => {
    mockProps.fetchWorkflowInstances.mockClear();
  });

  it('should have initially default state', () => {
    // given
    const instance = new ListView();
    // then
    expect(instance.state.entriesPerPage).toBe(0);
  });

  describe('display instances List', () => {
    it('should not contain a Footer when list is empty', () => {
      // given
      const node = shallow(Component);

      // then
      expect(node.find(ListFooter)).not.toExist();
    });

    it('should display the list and footer after the data is loaded', async () => {
      // given
      const node = shallow(ComponentWithInstances);

      node.setProps({instancesLoaded: true});
      node.update();

      // then
      expect(node.find(List)).toExist();
      expect(node.find(ListFooter)).toExist();
    });

    it('should pass properties to the Instances List', async () => {
      // given
      const node = shallow(ComponentWithInstances);

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
      node.setProps({
        instances: [{id: 1}],
        instancesLoaded: true
      });
      node.update();

      const changeFirstElement = node
        .find(ListFooter)
        .prop('onFirstElementChange');

      // then
      expect(changeFirstElement).toBeDefined();
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
  });
});
