import React from 'react';
import {shallow} from 'enzyme';

import {flushPromises, createInstance} from 'modules/testUtils';
import {EXPAND_STATE, DEFAULT_SORTING, DEFAULT_FILTER} from 'modules/constants';

import ListView from './ListView';
import List from './List';
import ListFooter from './ListFooter';

const defaultFilter = {DEFAULT_FILTER};
const filterCount = 27;
const onFirstElementChange = jest.fn();
const INSTANCE = createInstance();

const mockProps = {
  expandState: EXPAND_STATE.DEFAULT,
  filter: defaultFilter,
  filterCount: filterCount,
  instancesLoaded: false,
  instances: [],
  sorting: DEFAULT_SORTING,
  onSort: jest.fn(),
  firstElement: 0,
  onFirstElementChange: onFirstElementChange
};
const mockPropsWithInstances = {
  ...mockProps,
  instances: [INSTANCE],
  instancesLoaded: true
};
const Component = <ListView {...mockProps} />;
const ComponentWithInstances = <ListView {...mockPropsWithInstances} />;

describe('ListView', () => {
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
