/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow, mount} from 'enzyme';

import {ThemeProvider} from 'modules/theme';
import {SelectionProvider} from 'modules/contexts/SelectionContext';
import {InstancesPollProvider} from 'modules/contexts/InstancesPollContext';
import {CollapsablePanelProvider} from 'modules/contexts/CollapsablePanelContext';
import {HashRouter as Router} from 'react-router-dom';
import {formatGroupedWorkflows} from 'modules/utils/instance';
import {FILTER_SELECTION} from 'modules/constants';
import {
  flushPromises,
  createInstance,
  createOperation,
  mockResolvedAsyncFn,
  groupedWorkflowsMock
} from 'modules/testUtils';
import {EXPAND_STATE, DEFAULT_SORTING, DEFAULT_FILTER} from 'modules/constants';

import ListPanel from './ListPanel';
import List from './List';
import ListFooter from './ListFooter';

import * as api from 'modules/api/instances/instances';

// const ListPanelWrapped = ListPanel.WrappedComponent;

jest.mock('modules/utils/bpmn');

// mock props
const filterCount = 27;
const onFirstElementChange = jest.fn();
const INSTANCE = createInstance({
  id: '1',
  operations: [createOperation({state: 'FAILED'})],
  hasActiveOperation: false
});
const ACTIVE_INSTANCE = createInstance({
  id: '2',
  operations: [createOperation({state: 'SENT'})],
  hasActiveOperation: true
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
const Component = <ListPanel.WrappedComponent {...mockProps} />;
const ComponentWithInstances = (
  <ListPanel.WrappedComponent {...mockPropsWithInstances} />
);

// api mocks
api.fetchWorkflowInstances = mockResolvedAsyncFn([]);

describe('ListPanel', () => {
  it('should have initially default state', () => {
    // given
    const node = shallow(Component);
    // then
    expect(node.state().entriesPerPage).toBe(0);
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
      const node = mount(
        <Router>
          <ThemeProvider>
            <CollapsablePanelProvider>
              <SelectionProvider
                groupedWorkflows={formatGroupedWorkflows(groupedWorkflowsMock)}
                filter={FILTER_SELECTION.incidents}
              >
                <InstancesPollProvider>
                  <ListPanel {...mockPropsWithNoOperation} />
                </InstancesPollProvider>
              </SelectionProvider>
            </CollapsablePanelProvider>
          </ThemeProvider>
        </Router>
      );

      // when data fetched
      await flushPromises();
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
      api.fetchWorkflowInstances.mockClear();
      mockProps.onWorkflowInstancesRefresh.mockClear();
    });

    it('should not send ids for polling if no instances with active operations are displayed', async () => {
      const node = mount(
        <Router>
          <ThemeProvider>
            <CollapsablePanelProvider>
              <SelectionProvider
                groupedWorkflows={formatGroupedWorkflows(groupedWorkflowsMock)}
                filter={FILTER_SELECTION.incidents}
              >
                <InstancesPollProvider>
                  <ListPanel {...mockPropsWithNoOperation} />
                </InstancesPollProvider>
              </SelectionProvider>
            </CollapsablePanelProvider>
          </ThemeProvider>
        </Router>
      );

      await flushPromises();
      node.update();

      // no ids are sent for polling
      expect(node.find(InstancesPollProvider).state().ids).toEqual([]);
    });

    it('should send ids for polling if at least one instance with active operations is diplayed', async () => {
      const mockPropsWithPoll = {
        ...mockPropsWithInstances,
        polling: {
          ids: [],
          addIds: jest.fn(),
          removeIds: jest.fn()
        }
      };
      const node = shallow(
        <ListPanel.WrappedComponent {...mockPropsWithPoll} />
      );

      // when
      // simulate set number of visible rows from List
      node.setState({entriesPerPage: 2});

      // simulate change of instances displayed
      node.setProps({instancesLoaded: false});
      node.setProps({instancesLoaded: true});

      // then
      expect(mockPropsWithPoll.polling.addIds).toHaveBeenCalledWith([
        ACTIVE_INSTANCE.id
      ]);
    });

    it('should add the id to InstancesPollProvider after user starts operation on instance from list', async () => {
      // given
      const node = mount(
        <Router>
          <ThemeProvider>
            <CollapsablePanelProvider>
              <SelectionProvider
                groupedWorkflows={formatGroupedWorkflows(groupedWorkflowsMock)}
                filter={FILTER_SELECTION.incidents}
              >
                <InstancesPollProvider>
                  <ListPanel {...mockPropsWithNoOperation} />
                </InstancesPollProvider>
              </SelectionProvider>
            </CollapsablePanelProvider>
          </ThemeProvider>
        </Router>
      );

      // when
      await flushPromises();
      node.update();

      const onActionButtonClick = node.find(List).props().onActionButtonClick;

      onActionButtonClick(node.find(List).props().data[0]);
      node.update();

      // then
      expect(node.find(InstancesPollProvider).state().ids).toEqual([
        node.find(List).props().data[0].id
      ]);
    });

    it('should not poll for instances with active operations that are no longer in view after collapsing', async () => {
      const mockPropsWithPoll = {
        ...mockPropsWithNoOperation,
        polling: {
          ids: [],
          addIds: jest.fn(),
          removeIds: jest.fn()
        }
      };
      const node = shallow(
        <ListPanel.WrappedComponent {...mockPropsWithPoll} />
      );

      // when
      // simulate set number of visible rows from List
      node.setState({entriesPerPage: 2});

      // simulate change of instances displayed
      node.setProps({instancesLoaded: false});
      node.setProps({instancesLoaded: true});

      // then
      expect(mockPropsWithPoll.polling.addIds).not.toHaveBeenCalled();
    });

    // https://app.camunda.com/jira/browse/OPE-395
    it('should refetch instances when expanding the list panel', async () => {
      const mockPropsWithPoll = {
        ...mockPropsWithInstances,
        polling: {
          ids: [],
          addIds: jest.fn(),
          removeIds: jest.fn()
        }
      };
      const node = shallow(
        <ListPanel.WrappedComponent {...mockPropsWithPoll} />
      );

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
