import React from 'react';
import {shallow} from 'enzyme';

import {
  flushPromises,
  createInstance,
  createOperation,
  mockResolvedAsyncFn
} from 'modules/testUtils';
import {EXPAND_STATE, DEFAULT_SORTING, DEFAULT_FILTER} from 'modules/constants';

import ListView from './ListView';
import List from './List';
import ListFooter from './ListFooter';

import * as api from 'modules/api/instances/instances';

// mock props
const filterCount = 27;
const onFirstElementChange = jest.fn();
const INSTANCE = createInstance({
  id: '1',
  operations: [createOperation({state: 'FAILED'})]
});
const ACTIVE_INSTANCE = createInstance({
  id: '2',
  operations: [createOperation({state: 'SENT'})]
});

const mockProps = {
  expandState: EXPAND_STATE.DEFAULT,
  filter: DEFAULT_FILTER,
  filterCount: filterCount,
  instancesLoaded: false,
  instances: [],
  sorting: DEFAULT_SORTING,
  onSort: jest.fn(),
  firstElement: 0,
  onFirstElementChange: onFirstElementChange,
  onWorkflowInstancesRefresh: jest.fn()
};
const mockPropsWithInstances = {
  ...mockProps,
  instances: [INSTANCE, ACTIVE_INSTANCE],
  instancesLoaded: true
};

const mockPropsWithNoOperation = {
  ...mockProps,
  instances: [INSTANCE],
  instancesLoaded: true
};
const Component = <ListView {...mockProps} />;
const ComponentWithInstances = <ListView {...mockPropsWithInstances} />;

// api mocks
api.fetchWorkflowInstances = mockResolvedAsyncFn([]);

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

  describe('polling for instances changes', () => {
    beforeEach(() => {
      jest.useFakeTimers();
      api.fetchWorkflowInstances.mockClear();
      mockProps.onWorkflowInstancesRefresh.mockClear();
    });

    afterEach(() => {
      jest.clearAllTimers();
    });

    it('should not start polling after instances loaded if no instance with active operation', async () => {
      const node = shallow(<ListView {...mockPropsWithNoOperation} />);

      // when
      // simulate set number of visible rows from List
      node.setState({entriesPerPage: 2});

      // simulate change of instances displayed
      node.setProps({instancesLoaded: false});
      node.setProps({instancesLoaded: true});

      // no polling to start
      expect(setTimeout).toBeCalledTimes(0);
    });
    it('should start polling after instances loaded if at least one instance with active operations', async () => {
      const COMPLETED_ACTION_INSTANCE = createInstance({
        id: '2',
        operations: [createOperation({state: 'COMPLETED'})]
      });
      api.fetchWorkflowInstances = jest
        .fn()
        .mockResolvedValue({workflowInstances: [INSTANCE, ACTIVE_INSTANCE]}) // default
        .mockResolvedValueOnce({workflowInstances: [INSTANCE, ACTIVE_INSTANCE]}) // 1st call
        .mockResolvedValueOnce([INSTANCE, COMPLETED_ACTION_INSTANCE]); // 2nd call

      const node = shallow(ComponentWithInstances);

      // when
      // simulate set number of visible rows from List
      node.setState({entriesPerPage: 2});

      // simulate change of instances displayed
      node.setProps({instancesLoaded: false});
      node.setProps({instancesLoaded: true});

      // then
      // expect polling to start because we have one active operation for instance#2
      expect(setTimeout).toBeCalledTimes(1);

      // when first setTimeout is ran
      jest.runOnlyPendingTimers();
      await flushPromises();
      node.update();

      // expect component to fetch only instances with active operations
      expect(api.fetchWorkflowInstances).toHaveBeenCalledTimes(1);
      expect(
        api.fetchWorkflowInstances.mock.calls[0][0].queries[0].ids
      ).toEqual(['2']);

      // expect polling to continue as instance#2's operation is still active
      expect(setTimeout).toBeCalledTimes(2);

      // when 2nd setTimeout is ran
      jest.runOnlyPendingTimers();
      await flushPromises();
      node.update();

      // expect to fecth again instances by id to check operation status
      expect(api.fetchWorkflowInstances).toHaveBeenCalledTimes(2);
      expect(
        api.fetchWorkflowInstances.mock.calls[0][0].queries[0].ids
      ).toEqual(['2']);

      jest.runOnlyPendingTimers();
      // expect polling to stop, as instance#2 OPERATION's is now complete
      expect(setTimeout).toBeCalledTimes(2);
    });

    it('should start polling after user starts operation on instance from list', async () => {
      const ID = '111';
      const ACTIVE_INSTANCE = createInstance({
        id: ID,
        operations: [createOperation({state: 'SENT'})]
      });
      const INCIDENT_INSTANCE = createInstance({
        id: ID,
        operations: [createOperation({state: 'COMPLETE'})]
      });
      api.fetchWorkflowInstances = jest
        .fn()
        .mockResolvedValue({workflowInstances: [INCIDENT_INSTANCE]}) // default
        .mockResolvedValueOnce({workflowInstances: [ACTIVE_INSTANCE]}) // 1st call
        .mockResolvedValueOnce([INCIDENT_INSTANCE]); // 2nd call
      const node = shallow(<ListView {...mockPropsWithNoOperation} />);

      // when
      // simulate set number of visible rows from List
      node.setState({entriesPerPage: 2});

      // simulate change of instances displayed
      node.setProps({instancesLoaded: false});
      node.setProps({instancesLoaded: true});

      const onActionButtonClick = node.find(List).prop('onActionButtonClick');

      // simulate operation start on instance #111 from list
      onActionButtonClick(INCIDENT_INSTANCE);

      // expect polling to start after operation started
      expect(setTimeout).toBeCalledTimes(1);

      // when 1nd setTimeout is ran
      jest.runOnlyPendingTimers();
      await flushPromises();
      node.update();

      // expect the polling to fetch updates only for the clicked instance
      expect(api.fetchWorkflowInstances).toHaveBeenCalledTimes(1);
      expect(
        api.fetchWorkflowInstances.mock.calls[0][0].queries[0].ids
      ).toEqual([ID]);

      // expect polling to continue
      expect(setTimeout).toBeCalledTimes(2);

      // when 2nd setTimeout is ran, the instance's operation completes
      jest.runOnlyPendingTimers();
      await flushPromises();
      node.update();

      // we expect the polling to stop
      expect(setTimeout).toBeCalledTimes(2);
    });

    it('should not poll for instances with active operations that are no longer in view after collapsing', async () => {
      const node = shallow(ComponentWithInstances);

      // when
      // simulate set number of visible rows from List
      node.setState({entriesPerPage: 2});
      node.update();

      // simulate load of instances in list
      node.setProps({instancesLoaded: false});
      node.setProps({instancesLoaded: true});

      // expect polling to start for active instance in list
      expect(setTimeout).toBeCalledTimes(1);

      // run the poll function
      jest.runOnlyPendingTimers();
      await flushPromises();
      node.update();

      // simulate collapsing the list panel
      node.setState({entriesPerPage: 1});

      // expect polling to stop, as instance with active is now hidden
      expect(setTimeout).toBeCalledTimes(1);
    });

    // https://app.camunda.com/jira/browse/OPE-395
    it('should refetch instances when expanding the list panel', async () => {
      const node = shallow(ComponentWithInstances);

      // when
      // simulate set number of visible rows from List
      node.setState({entriesPerPage: 2});

      // simulate load of instances in list
      node.setProps({instancesLoaded: false});
      node.setProps({instancesLoaded: true});

      node.setState({entriesPerPage: 2});
      await flushPromises();
      node.update();

      expect(mockProps.onWorkflowInstancesRefresh).toHaveBeenCalledTimes(1);
    });
  });
});
