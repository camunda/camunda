/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

import {DataManager} from 'modules/DataManager/core';

import InstancesContainer from './InstancesContainer';
import Instances from './Instances';

import {parseQueryString, decodeFields} from './service';
import * as api from 'modules/api/instances/instances';
import * as apiDiagram from 'modules/api/diagram/diagram';
import {getFilterQueryString} from 'modules/utils/filter';
import {formatGroupedWorkflows} from 'modules/utils/instance';
import {
  DEFAULT_FILTER,
  DEFAULT_SORTING,
  DEFAULT_FIRST_ELEMENT,
  DEFAULT_MAX_RESULTS,
  SORT_ORDER,
  INCIDENTS_FILTER,
  LOADING_STATE
} from 'modules/constants';
import {
  mockResolvedAsyncFn,
  flushPromises,
  groupedWorkflowsMock,
  createMockInstancesObject
} from 'modules/testUtils';
import {parsedDiagram} from 'modules/utils/bpmn';
const InstancesContainerWrapped = InstancesContainer.WrappedComponent;

// component mocks
jest.mock(
  './Instances',
  () =>
    function Instances(props) {
      return <div />;
    }
);

jest.mock('modules/DataManager/core');

const subscribeMock = jest.fn(); // Lets you check if `connect()` was called, if you want
const getWorkflowXMLMock = jest.fn();
const getWorkflowInstancesMock = jest.fn();
const getWorkflowInstancesStatisticsMock = jest.fn();

DataManager.mockImplementation(() => {
  return {
    subscribe: subscribeMock,
    getWorkflowXML: getWorkflowXMLMock,
    getWorkflowInstances: getWorkflowInstancesMock,
    getWorkflowInstancesStatistics: getWorkflowInstancesStatisticsMock
  };
});

// props mocks
const fullFilterWithoutWorkflow = {
  active: true,
  incidents: true,
  completed: true,
  canceled: true,
  ids: '424242, 434343',
  errorMessage: 'No%20data%20found%20for%20query%20$.foo.',
  startDate: '2018-12-28',
  endDate: '2018-12-28'
};

const fullFilterWithWorkflow = {
  active: true,
  incidents: true,
  completed: true,
  canceled: true,
  ids: '424242, 434343',
  errorMessage: 'No%20data%20found%20for%20query%20$.foo.',
  startDate: '2018-12-28',
  endDate: '2018-12-28',
  workflow: 'demoProcess',
  version: 1
  // activityId: 'taskD'
};

const localStorageProps = {
  getStateLocally: jest.fn(),
  storeStateLocally: jest.fn()
};

// api mocks
api.fetchGroupedWorkflows = mockResolvedAsyncFn(groupedWorkflowsMock);
const mockInstances = createMockInstancesObject();
api.fetchWorkflowInstances = mockResolvedAsyncFn(mockInstances);
api.fetchWorkflowInstancesStatistics = mockResolvedAsyncFn([]);
apiDiagram.fetchWorkflowXML = mockResolvedAsyncFn('<xml />');
jest.mock('bpmn-js', () => ({}));
jest.mock('modules/utils/bpmn');

// local utility
const pushMock = jest.fn();
const listenMock = jest.fn();
function getRouterProps(filter = DEFAULT_FILTER) {
  return {
    history: {push: pushMock, listen: () => listenMock, location: {search: ''}},
    location: {
      search: getFilterQueryString(filter)
    }
  };
}

describe('InstancesContainer', () => {
  let dataManager;
  beforeEach(() => {
    jest.clearAllMocks();
    dataManager = new DataManager();
  });

  describe('mounting', () => {
    it('should fetch the groupedWorkflows', async () => {
      // given
      const node = mount(
        <InstancesContainerWrapped
          {...localStorageProps}
          {...getRouterProps()}
          {...{dataManager}}
        />
      );

      // when
      await flushPromises();
      node.update();

      // then
      expect(api.fetchGroupedWorkflows).toHaveBeenCalled();
    });

    it('should fetch the workflow xml', async () => {
      const node = mount(
        <InstancesContainerWrapped
          {...localStorageProps}
          {...getRouterProps(fullFilterWithWorkflow)}
          {...{dataManager}}
        />
      );

      dataManager.getWorkflowXML = jest.fn(() => {
        node.instance().subscriptions['LOAD_STATE_DEFINITIONS']({
          state: LOADING_STATE.LOADED,
          response: parsedDiagram
        });
      });

      // when
      await flushPromises();
      node.update();

      //then
      expect(dataManager.getWorkflowXML).toHaveBeenCalledWith(
        groupedWorkflowsMock[0].workflows[2].id
      );

      expect(node.find('Instances').prop('diagramModel')).toEqual(
        parsedDiagram
      );
    });

    // How to test the logic of the callback, which are subscribed to topics?
    it.skip('should fetch statistics', async () => {
      // given
      const node = mount(
        <InstancesContainerWrapped
          {...{dataManager}}
          {...localStorageProps}
          {...getRouterProps(fullFilterWithWorkflow)}
        />
      );
      const expectedQuery = {
        active: true,
        activityId: 'taskD',
        completed: true,
        errorMessage: 'No data found for query $.foo.',
        finished: true,
        ids: ['424242', '434343'],
        incidents: true,
        running: true,
        workflowIds: ['1']
      };

      // when
      await flushPromises();
      node.update();

      // then
      expect(dataManager.getWorkflowInstancesStatistics).toHaveBeenCalled();
      expect(
        dataManager.getWorkflowInstancesStatistics.mock.calls[0][0].queries[0]
      ).toMatchObject(expectedQuery);
    });
  });

  describe('fetching workflow instances', () => {
    const expectedQuery = {
      active: true,

      completed: true,
      errorMessage: 'No data found for query $.foo.',
      finished: true,
      ids: ['424242', '434343'],
      incidents: true,
      running: true,
      workflowIds: ['1']
    };
    let node;
    beforeEach(() => {
      dataManager.getWorkflowInstances.mockClear();
      node = mount(
        <InstancesContainerWrapped
          {...{dataManager}}
          {...localStorageProps}
          {...getRouterProps(fullFilterWithWorkflow)}
        />
      );

      dataManager.getWorkflowInstances = jest.fn(() => {
        node.instance().subscriptions['LOAD_STATE_INSTANCES']({
          state: LOADING_STATE.LOADED,
          response: expectedQuery
        });
      });
    });

    it('should fetch instances when filter changes', async () => {
      // given

      // when
      await flushPromises();
      node.update();

      // thenw
      expect(dataManager.getWorkflowInstances).toHaveBeenCalled();
      const call = dataManager.getWorkflowInstances.mock.calls[0][0];
      expect(call.queries[0]).toMatchObject({
        ...expectedQuery
      });
      expect(call.firstResult).toEqual(node.state('firstElement'));
      expect(call.maxResults).toEqual(DEFAULT_MAX_RESULTS);
    });

    it('should fetch instances when sorting changes', async () => {
      // when
      node.find('Instances').prop('onSort')(DEFAULT_SORTING.sortBy);

      await flushPromises();
      node.update();

      // then
      expect(dataManager.getWorkflowInstances).toHaveBeenCalled();
      const call = dataManager.getWorkflowInstances.mock.calls[0][0];
      expect(call.queries[0]).toMatchObject({
        ...expectedQuery
      });
      expect(call.firstResult).toEqual(node.state('firstElement'));
      expect(call.maxResults).toEqual(DEFAULT_MAX_RESULTS);
    });

    it('should fetch instances when firstElement changes', async () => {
      // given
      const node = mount(
        <InstancesContainerWrapped
          {...{dataManager}}
          {...localStorageProps}
          {...getRouterProps(fullFilterWithWorkflow)}
        />
      );
      await flushPromises();
      node.update();
      api.fetchWorkflowInstances.mockClear();

      // when
      node.find('Instances').prop('onFirstElementChange')(3);
      await flushPromises();
      node.update();

      // then
      expect(dataManager.getWorkflowInstances).toHaveBeenCalled();
      const call = dataManager.getWorkflowInstances.mock.calls[0][0];
      expect(call.queries[0]).toMatchObject({
        ...expectedQuery
      });
      expect(call.maxResults).toEqual(DEFAULT_MAX_RESULTS);
    });
  });

  it('should write the filter to local storage', async () => {
    // given
    const node = mount(
      <InstancesContainerWrapped
        {...{dataManager}}
        {...localStorageProps}
        {...getRouterProps()}
      />
    );

    // when
    node.setState({filter: {...DEFAULT_FILTER, running: false}});
    await flushPromises();
    node.update();

    // then
    expect(localStorageProps.storeStateLocally).toHaveBeenCalled();
  });

  it('should render the Instances', () => {
    const node = mount(
      <InstancesContainerWrapped
        {...{dataManager}}
        {...localStorageProps}
        {...getRouterProps()}
      />
    );

    expect(node.find(Instances)).toExist();
  });

  it('should pass data to Instances for default filter', async () => {
    // given
    const node = mount(
      <InstancesContainerWrapped
        {...{dataManager}}
        {...localStorageProps}
        {...getRouterProps()}
      />
    );

    // when
    await flushPromises();
    node.update();

    const InstancesNode = node.find(Instances);

    // then
    expect(InstancesNode.prop('groupedWorkflows')).toEqual(
      formatGroupedWorkflows(groupedWorkflowsMock)
    );

    expect(InstancesNode.prop('filter')).toEqual({
      ...DEFAULT_FILTER
    });
    expect(InstancesNode.props().diagramModel).toEqual({});
  });

  it('should pass data to Instances for full filter, without workflow data', async () => {
    // given
    const node = mount(
      <InstancesContainerWrapped
        {...{dataManager}}
        {...localStorageProps}
        {...getRouterProps(decodeFields(fullFilterWithoutWorkflow))}
      />
    );

    // when
    await flushPromises();
    node.update();

    const InstancesNode = node.find(Instances);

    expect(InstancesNode.prop('filter')).toEqual(
      decodeFields(fullFilterWithoutWorkflow)
    );
    expect(InstancesNode.prop('diagramModel')).toEqual({});
  });

  it('should pass data to Instances for full filter, with workflow data', async () => {
    const node = mount(
      <InstancesContainerWrapped
        {...{dataManager}}
        {...localStorageProps}
        {...getRouterProps(fullFilterWithWorkflow)}
      />
    );

    dataManager.getWorkflowXML = jest.fn(() => {
      node.instance().subscriptions['LOAD_STATE_DEFINITIONS']({
        state: LOADING_STATE.LOADED,
        response: parsedDiagram
      });
    });

    // when
    await flushPromises();
    node.update();

    const InstancesNode = node.find(Instances);

    expect(InstancesNode.prop('filter')).toEqual(
      decodeFields({...fullFilterWithWorkflow})
    );
    expect(InstancesNode.prop('diagramModel')).toEqual(parsedDiagram);
  });

  it('should pass data to Instances for full filter, with all versions', async () => {
    const {activityId, version, ...rest} = fullFilterWithWorkflow;
    const node = mount(
      <InstancesContainerWrapped
        {...{dataManager}}
        {...localStorageProps}
        {...getRouterProps({
          ...rest,
          version: 'all'
        })}
      />
    );

    // when
    await flushPromises();
    node.update();

    const InstancesNode = node.find(Instances);

    expect(InstancesNode.prop('filter')).toEqual(
      decodeFields({
        ...rest,
        version: 'all'
      })
    );
    expect(InstancesNode.prop('diagramModel')).toEqual({});
  });

  describe('reading url filter', () => {
    // somehow it can't pass the condition in componentDidUpdate.
    it.skip('should update filters on URL changes', async () => {
      // given
      const routerProps = getRouterProps();
      const newRouterProps = getRouterProps(fullFilterWithWorkflow);

      const node = mount(
        <InstancesContainerWrapped
          {...{dataManager}}
          {...localStorageProps}
          {...routerProps}
        />
      );
      const setFilterFromUrlSpy = jest.spyOn(node.instance(), 'setFilterInURL');

      // when
      await flushPromises();
      node.update();

      // when componentDidMount
      expect(setFilterFromUrlSpy).toHaveBeenCalledTimes(1);

      node.setProps({...newRouterProps});
      node.update();

      // when componentDidUpdate & Url changed
      expect(setFilterFromUrlSpy).toHaveBeenCalledTimes(2);
    });

    describe('fixing an invalid filter in url', () => {
      it('should add the default filter to the url when no filter is present', async () => {
        const noFilterRouterProps = {
          history: {push: jest.fn(), location: ''},
          location: {
            search: ''
          }
        };

        mount(
          <InstancesContainerWrapped
            {...{dataManager}}
            {...localStorageProps}
            {...noFilterRouterProps}
          />
        );

        // when
        await flushPromises();

        // then
        const encodedFilter = encodeURIComponent(
          '{"active":true,"incidents":true}'
        );
        expect(noFilterRouterProps.history.push).toHaveBeenCalled();
        expect(noFilterRouterProps.history.push.mock.calls[0][0].search).toBe(
          `?filter=${encodedFilter}`
        );
      });

      it('when a value in the filter in url is invalid', async () => {
        const invalidFilterRouterProps = {
          history: {push: jest.fn(), location: ''},
          location: {
            search:
              '?filter={"active": fallse, "errorMessage": "No%20data%20found%20for%20query%20$.foo."'
          }
        };
        const node = mount(
          <InstancesContainerWrapped
            {...{dataManager}}
            {...localStorageProps}
            {...invalidFilterRouterProps}
          />
        );

        // when
        await flushPromises();
        node.update();

        // then
        const encodedFilter = encodeURIComponent(
          '{"active":true,"incidents":true}'
        );
        expect(invalidFilterRouterProps.history.push).toHaveBeenCalled();
        expect(
          invalidFilterRouterProps.history.push.mock.calls[0][0].search
        ).toBe(`?filter=${encodedFilter}`);
      });

      it('when the workflow in url is invalid', async () => {
        const node = mount(
          <InstancesContainerWrapped
            {...{dataManager}}
            {...localStorageProps}
            {...getRouterProps({
              ...fullFilterWithWorkflow,
              workflow: 'x'
            })}
          />
        );

        // when
        await flushPromises();
        node.update();

        // expect invalid activityId to have been removed
        expect(pushMock).toHaveBeenCalled();
        const search = pushMock.mock.calls[0][0].search;
        const {version, workflow, activityId, ...rest} = fullFilterWithWorkflow;

        expect(parseQueryString(search).filter).toEqual(rest);
      });

      it('when the version in url is invalid', async () => {
        const node = mount(
          <InstancesContainerWrapped
            {...{dataManager}}
            {...localStorageProps}
            {...getRouterProps({
              ...fullFilterWithWorkflow,
              version: 'x'
            })}
          />
        );

        // when
        await flushPromises();
        node.update();

        // expect invalid activityId to have been removed
        expect(pushMock).toHaveBeenCalled();
        const search = pushMock.mock.calls[0][0].search;
        const {version, workflow, activityId, ...rest} = fullFilterWithWorkflow;

        expect(parseQueryString(search).filter).toEqual(rest);
      });

      it('when the activityId in url is invalid', async () => {
        const node = mount(
          <InstancesContainerWrapped
            {...{dataManager}}
            {...localStorageProps}
            {...getRouterProps({
              ...fullFilterWithWorkflow,
              activityId: 'x'
            })}
          />
        );

        // when
        await flushPromises();
        node.update();

        // expect invalid activityId to have been removed
        expect(pushMock).toHaveBeenCalledTimes(1);
        const search = pushMock.mock.calls[0][0].search;
        const {activityId, ...rest} = fullFilterWithWorkflow;
        expect(parseQueryString(search).filter).toEqual(rest);
      });

      it('should remove activityId when version="all"', async () => {
        const node = mount(
          <InstancesContainerWrapped
            {...{dataManager}}
            {...localStorageProps}
            {...getRouterProps({
              ...fullFilterWithWorkflow,
              version: 'all',
              activityId: 'taskD'
            })}
          />
        );

        // when
        await flushPromises();
        node.update();

        // expect invalid activityId to have been removed
        expect(pushMock).toHaveBeenCalledTimes(1);
        const search = pushMock.mock.calls[0][0].search;
        const {activityId, version, ...rest} = fullFilterWithWorkflow;
        expect(parseQueryString(search).filter).toEqual({
          ...rest,
          version: 'all'
        });
      });
    });

    describe('updating the state when filter is valid', () => {
      it('should update the state for a valid filter with no workflow', async () => {
        const node = mount(
          <InstancesContainerWrapped
            {...{dataManager}}
            {...localStorageProps}
            {...getRouterProps({
              ...fullFilterWithoutWorkflow
            })}
          />
        );

        // when
        await flushPromises();
        node.update();

        // then
        const InstancesNode = node.find('Instances');
        expect(InstancesNode.prop('filter')).toEqual(
          decodeFields(fullFilterWithoutWorkflow)
        );
        expect(InstancesNode.prop('diagramModel')).toEqual({});
        expect(InstancesNode.prop('statistics')).toEqual([]);
        expect(InstancesNode.prop('firstElement')).toBe(DEFAULT_FIRST_ELEMENT);
        expect(InstancesNode.prop('sorting')).toEqual(DEFAULT_SORTING);
      });

      it('should update the state for a valid filter with version="all"', async () => {
        // given
        const {activityId, ...filterWithoutActivityId} = fullFilterWithWorkflow;
        const validFilterWithVersionAll = {
          ...filterWithoutActivityId,
          version: 'all'
        };
        const node = mount(
          <InstancesContainerWrapped
            {...{dataManager}}
            {...localStorageProps}
            {...getRouterProps(validFilterWithVersionAll)}
          />
        );

        // when
        await flushPromises();
        node.update();

        // then
        const InstancesNode = node.find('Instances');
        expect(InstancesNode.prop('filter')).toEqual(
          decodeFields(validFilterWithVersionAll)
        );
        expect(InstancesNode.prop('diagramModel')).toEqual({});
        expect(InstancesNode.prop('statistics')).toEqual([]);
        expect(InstancesNode.prop('firstElement')).toBe(DEFAULT_FIRST_ELEMENT);
        expect(InstancesNode.prop('sorting')).toEqual(DEFAULT_SORTING);
      });

      it("should update the state for a valid filter when workflow didn't change", async () => {
        // given
        const node = mount(
          <InstancesContainerWrapped
            {...{dataManager}}
            {...localStorageProps}
            {...getRouterProps(fullFilterWithWorkflow)}
          />
        );
        await flushPromises();
        node.update();

        // when
        // change filter without chaning workflow
        const newFilter = {
          ...fullFilterWithWorkflow,
          endDate: '1955-12-28'
        };
        node.find('Instances').prop('onFilterChange')(newFilter);
        await flushPromises();
        node.update();

        // then
        const InstancesNode = node.find('Instances');
        expect(InstancesNode.prop('filter')).toEqual(decodeFields(newFilter));
        expect(InstancesNode.prop('firstElement')).toBe(DEFAULT_FIRST_ELEMENT);
        expect(InstancesNode.prop('sorting')).toEqual(DEFAULT_SORTING);
      });

      it('should update the state for a valid filter when workflow changes', async () => {
        // given
        const node = mount(
          <InstancesContainerWrapped
            {...{dataManager}}
            {...localStorageProps}
            {...getRouterProps(fullFilterWithWorkflow)}
          />
        );
        await flushPromises();
        node.update();

        dataManager.getWorkflowXML = jest.fn(() => {
          node.instance().subscriptions['LOAD_STATE_DEFINITIONS']({
            state: LOADING_STATE.LOADED,
            response: parsedDiagram
          });
        });

        // when
        // change workflow by changing the version
        const newFilter = {...fullFilterWithWorkflow, version: '3'};
        node.find('Instances').prop('onFilterChange')(newFilter);
        await flushPromises();
        node.update();

        // then
        const InstancesNode = node.find('Instances');
        expect(InstancesNode.prop('filter')).toEqual(decodeFields(newFilter));
        // update diagramModel
        expect(InstancesNode.prop('diagramModel')).toEqual(parsedDiagram);
        // // reset statistics
        expect(InstancesNode.prop('statistics')).toEqual([]);
        expect(InstancesNode.prop('firstElement')).toBe(DEFAULT_FIRST_ELEMENT);
        expect(InstancesNode.prop('sorting')).toEqual(DEFAULT_SORTING);
      });
    });
  });

  describe('handle filter change', () => {
    it("should update filter in url if it's different from the current one", async () => {
      const node = mount(
        <InstancesContainerWrapped
          {...{dataManager}}
          {...localStorageProps}
          {...getRouterProps(fullFilterWithoutWorkflow)}
        />
      );

      await flushPromises();
      node.update();
      const InstancesNode = node.find('Instances');

      // when
      InstancesNode.prop('onFilterChange')(fullFilterWithWorkflow);

      // then
      expect(pushMock).toHaveBeenCalled();
      expect(pushMock.mock.calls[0][0].search).toBe(
        getFilterQueryString({
          ...fullFilterWithoutWorkflow,
          ...fullFilterWithWorkflow,
          variable: ''
        })
      );
    });

    it('should handle filter reset', async () => {
      const node = mount(
        <InstancesContainerWrapped
          {...{dataManager}}
          {...localStorageProps}
          {...getRouterProps(fullFilterWithoutWorkflow)}
        />
      );

      await flushPromises();
      node.update();
      const InstancesNode = node.find('Instances');

      // when
      InstancesNode.prop('onFilterReset')(DEFAULT_FILTER);

      // then
      expect(pushMock).toHaveBeenCalled();
      expect(pushMock.mock.calls[0][0].search).toBe(
        getFilterQueryString(DEFAULT_FILTER)
      );
    });
  });

  describe('sorting', () => {
    it('should be able to handle sorting change', async () => {
      // given
      const node = mount(
        <InstancesContainerWrapped
          {...{dataManager}}
          {...localStorageProps}
          {...getRouterProps(fullFilterWithoutWorkflow)}
        />
      );
      await flushPromises();
      node.update();

      // when
      node.find('Instances').prop('onSort')(DEFAULT_SORTING.sortBy);
      await flushPromises();
      node.update();

      // then
      const expectedSorting = {...DEFAULT_SORTING, sortOrder: SORT_ORDER.ASC};
      expect(node.find('Instances').prop('sorting')).toEqual(expectedSorting);
    });

    it('should reset sorting if it changes and no finished filter is active', async () => {
      // given
      const node = mount(
        <InstancesContainerWrapped
          {...{dataManager}}
          {...localStorageProps}
          {...getRouterProps(INCIDENTS_FILTER)}
        />
      );
      await flushPromises();
      node.update();

      // when
      node.find('Instances').prop('onSort')('endDate');
      await flushPromises();
      node.update();

      // then
      expect(node.find('Instances').prop('sorting')).toEqual(DEFAULT_SORTING);
    });

    it('should reset sorting if filter changes and no finished filter is active', async () => {
      // given
      const filterWithCompleted = {...INCIDENTS_FILTER, completed: true};
      const node = mount(
        <InstancesContainerWrapped
          {...{dataManager}}
          {...localStorageProps}
          {...getRouterProps(filterWithCompleted)}
        />
      );
      await flushPromises();
      node.update();
      node.find('Instances').prop('onSort')('endDate');
      await flushPromises();
      node.update();

      // when
      node.find('Instances').prop('onFilterReset')(DEFAULT_FILTER);
      await flushPromises();
      node.update();

      // then
      expect(node.find('Instances').prop('sorting')).toEqual(DEFAULT_SORTING);
    });
  });

  describe('handleWorkflowInstancesRefresh', () => {
    it('should pass handleWorkflowInstancesRefresh to Instances', () => {
      const node = mount(
        <InstancesContainerWrapped
          {...{dataManager}}
          {...localStorageProps}
          {...getRouterProps()}
        />
      );

      expect(
        node.find(Instances).props().onWorkflowInstancesRefresh
      ).toBeDefined();
    });

    it('should refetch the workflow instances', async () => {
      const MOCK = {workflowInstances: ['1', '2'], totalCount: 2};
      api.fetchWorkflowInstances = mockResolvedAsyncFn(MOCK);

      const node = mount(
        <InstancesContainerWrapped
          {...{dataManager}}
          {...localStorageProps}
          {...getRouterProps(fullFilterWithoutWorkflow)}
        />
      );

      // when
      await flushPromises();
      node.update();

      const onWorkflowInstancesRefresh = node.find(Instances).props()
        .onWorkflowInstancesRefresh;

      // when
      onWorkflowInstancesRefresh();
      node.update();
      await flushPromises();

      expect(dataManager.getWorkflowInstances).toHaveBeenCalledTimes(2);
      expect(dataManager.getWorkflowInstancesStatistics).toHaveBeenCalledTimes(
        0
      );
    });

    it('should refetch and set the statistics when a diagram is visible', async () => {
      const MOCK = ['statistics'];
      api.fetchWorkflowInstancesStatistics = mockResolvedAsyncFn(MOCK);
      const node = mount(
        <InstancesContainerWrapped
          {...{dataManager}}
          {...localStorageProps}
          {...getRouterProps(fullFilterWithWorkflow)}
        />
      );

      node.setState({diagramModel: {definitions: [], bpmnElements: []}});
      // when
      await flushPromises();
      node.update();

      const onWorkflowInstancesRefresh = node.find(Instances).props()
        .onWorkflowInstancesRefresh;

      onWorkflowInstancesRefresh();

      await flushPromises();
      node.update();

      expect(dataManager.getWorkflowInstancesStatistics).toHaveBeenCalledTimes(
        1
      );
    });
  });
});
