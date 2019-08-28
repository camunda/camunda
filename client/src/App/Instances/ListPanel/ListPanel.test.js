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
import {DataManager} from 'modules/DataManager/core';

import {HashRouter as Router} from 'react-router-dom';
import {formatGroupedWorkflows} from 'modules/utils/instance';
import {FILTER_SELECTION} from 'modules/constants';
import {
  flushPromises,
  mockResolvedAsyncFn,
  groupedWorkflowsMock
} from 'modules/testUtils';

import {
  mockProps,
  mockPropsWithInstances,
  mockPropsWithNoOperation,
  mockPropsWithPoll,
  ACTIVE_INSTANCE
} from './ListPanel.setup';

import ListPanel from './ListPanel';
import List from './List';
import ListFooter from './ListFooter';

import * as api from 'modules/api/instances/instances';

jest.mock('modules/utils/bpmn');
jest.mock('modules/DataManager/core');

DataManager.mockImplementation(() => {
  return {
    subscribe: jest.fn(),
    getWorkflowXML: jest.fn(),
    getWorkflowInstances: jest.fn(),
    getWorkflowInstancesStatistics: jest.fn()
  };
});

// api mocks
api.fetchWorkflowInstances = mockResolvedAsyncFn([]);

describe('ListPanel', () => {
  let Component, ComponentWithInstances;
  let dataManager;
  beforeEach(() => {
    jest.clearAllMocks();
    dataManager = new DataManager();

    Component = (
      <ListPanel.WrappedComponent {...mockProps} {...{dataManager}} />
    );
    ComponentWithInstances = (
      <ListPanel.WrappedComponent
        {...mockPropsWithInstances}
        {...{dataManager}}
      />
    );
  });

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
                  <ListPanel.WrappedComponent
                    {...mockPropsWithNoOperation}
                    {...{dataManager}}
                  />
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
                  <ListPanel.WrappedComponent
                    {...mockPropsWithNoOperation}
                    {...{dataManager}}
                  />
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
        <ListPanel.WrappedComponent {...mockPropsWithPoll} {...{dataManager}} />
      );

      // when
      // simulate set number of visible rows from List
      node.setState({entriesPerPage: 2});

      // simulate change of instances displayed
      node.setState({instancesLoaded: false});
      node.setState({instancesLoaded: true});

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
                  <ListPanel.WrappedComponent
                    {...mockPropsWithPoll}
                    {...{dataManager}}
                  />
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
      expect(mockPropsWithPoll.polling.addIds).toHaveBeenCalledWith([
        node.find(List).props().data[0].id
      ]);
    });

    it('should not poll for instances with active operations that are no longer in view after collapsing', async () => {
      const node = shallow(
        <ListPanel.WrappedComponent {...mockPropsWithPoll} {...{dataManager}} />
      );

      // when
      // simulate set number of visible rows from List
      node.setState({entriesPerPage: 2});

      // simulate change of instances displayed
      node.setState({instancesLoaded: false});
      node.setState({instancesLoaded: true});

      // then
      expect(mockPropsWithPoll.polling.addIds).not.toHaveBeenCalled();
    });

    // https://app.camunda.com/jira/browse/OPE-395
    it('should refetch instances when expanding the list panel', async () => {
      const node = shallow(
        <ListPanel.WrappedComponent {...mockPropsWithPoll} {...{dataManager}} />
      );

      // when
      // simulate set number of visible rows from List
      node.setState({entriesPerPage: 2});

      // simulate load of instances in list
      node.setState({instancesLoaded: false});
      node.setState({instancesLoaded: true});

      node.setState({entriesPerPage: 3});
      await flushPromises();
      node.update();

      expect(
        mockPropsWithPoll.onWorkflowInstancesRefresh
      ).toHaveBeenCalledTimes(1);
    });
  });
});
