/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

import InstancesContainer from './InstancesContainer';
import Instances from './Instances';

import {decodeFields} from './service';
import {parseQueryString} from 'modules/utils/filter';
import * as api from 'modules/api/instances/instances';
import * as apiDiagram from 'modules/api/diagram/diagram';
import * as filterUtils from 'modules/utils/filter/filter';

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

import {
  mockFullFilterWithoutWorkflow,
  mockFullFilterWithWorkflow,
  mockLocalStorageProps
} from './InstancesContainer.setup';

import {createMockDataManager} from 'modules/testHelpers/dataManager';

const InstancesContainerWrapped = InstancesContainer.WrappedComponent;

// component mocks
jest.mock(
  './Instances',
  () =>
    function Instances(props) {
      return <div />;
    }
);

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
// utility mock;

function getRouterProps(filter = DEFAULT_FILTER) {
  return {
    history: {push: pushMock, listen: () => listenMock, location: {search: ''}},
    location: {
      search: filterUtils.getFilterQueryString(filter)
    }
  };
}

describe('InstancesContainer', () => {
  let dataManager;

  beforeEach(() => {
    jest.clearAllMocks();

    dataManager = createMockDataManager();
  });

  describe('mounting', () => {
    it('should fetch the groupedWorkflows', async () => {
      // given
      const node = mount(
        <InstancesContainerWrapped
          {...mockLocalStorageProps}
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
          {...mockLocalStorageProps}
          {...getRouterProps(mockFullFilterWithWorkflow)}
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
    it('should fetch statistics', async () => {
      // given
      const node = mount(
        <InstancesContainerWrapped
          {...{dataManager}}
          {...mockLocalStorageProps}
          {...getRouterProps(mockFullFilterWithWorkflow)}
        />
      );

      // when
      await flushPromises();
      node.update();

      const subscriptions = node.instance().subscriptions;
      dataManager.publish({
        subscription: subscriptions['LOAD_STATE_DEFINITIONS'],
        state: LOADING_STATE.LOADED,
        response: parsedDiagram
      });

      // then
      expect(dataManager.getWorkflowInstancesStatistics).toHaveBeenCalled();
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
          {...mockLocalStorageProps}
          {...getRouterProps(mockFullFilterWithWorkflow)}
        />
      );

      dataManager.getWorkflowInstances = jest.fn(() => {
        node.instance().subscriptions['LOAD_LIST_INSTANCES']({
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
          {...mockLocalStorageProps}
          {...getRouterProps(mockFullFilterWithWorkflow)}
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
        {...mockLocalStorageProps}
        {...getRouterProps()}
      />
    );

    // when
    node.setState({filter: {...DEFAULT_FILTER, running: false}});
    await flushPromises();
    node.update();

    // then
    expect(mockLocalStorageProps.storeStateLocally).toHaveBeenCalled();
  });

  it('should render the Instances', () => {
    const node = mount(
      <InstancesContainerWrapped
        {...{dataManager}}
        {...mockLocalStorageProps}
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
        {...mockLocalStorageProps}
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
        {...mockLocalStorageProps}
        {...getRouterProps(decodeFields(mockFullFilterWithoutWorkflow))}
      />
    );

    // when
    await flushPromises();
    node.update();

    const InstancesNode = node.find(Instances);

    expect(InstancesNode.prop('filter')).toEqual(
      decodeFields(mockFullFilterWithoutWorkflow)
    );
    expect(InstancesNode.prop('diagramModel')).toEqual({});
  });

  it('should pass data to Instances for full filter, with workflow data', async () => {
    const node = mount(
      <InstancesContainerWrapped
        {...{dataManager}}
        {...mockLocalStorageProps}
        {...getRouterProps(mockFullFilterWithWorkflow)}
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
      decodeFields({...mockFullFilterWithWorkflow})
    );
    expect(InstancesNode.prop('diagramModel')).toEqual(parsedDiagram);
  });

  it('should pass data to Instances for full filter, with all versions', async () => {
    const {activityId, version, ...rest} = mockFullFilterWithWorkflow;
    const node = mount(
      <InstancesContainerWrapped
        {...{dataManager}}
        {...mockLocalStorageProps}
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
    it('should update filters on URL changes', async () => {
      // given
      const routerProps = getRouterProps();
      const newRouterProps = getRouterProps(mockFullFilterWithWorkflow);

      const node = mount(
        <InstancesContainerWrapped
          {...{dataManager}}
          {...mockLocalStorageProps}
          {...routerProps}
        />
      );
      const setFilterFromUrlSpy = jest.spyOn(node.instance(), 'setFilterInURL');

      await flushPromises();
      node.update();

      // when
      node.setProps({...newRouterProps});
      node.update();

      // when componentDidUpdate & Url changed
      expect(setFilterFromUrlSpy).toHaveBeenCalledTimes(1);
      expect(setFilterFromUrlSpy).toHaveBeenCalledWith(
        mockFullFilterWithWorkflow
      );
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
            {...mockLocalStorageProps}
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
            {...mockLocalStorageProps}
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
            {...mockLocalStorageProps}
            {...getRouterProps({
              ...mockFullFilterWithWorkflow,
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
        const {
          version,
          workflow,
          activityId,
          ...rest
        } = mockFullFilterWithWorkflow;

        expect(parseQueryString(search).filter).toEqual(rest);
      });

      it('when the version in url is invalid', async () => {
        const node = mount(
          <InstancesContainerWrapped
            {...{dataManager}}
            {...mockLocalStorageProps}
            {...getRouterProps({
              ...mockFullFilterWithWorkflow,
              version: 'x'
            })}
          />
        );
        const subscriptions = node.instance().subscriptions;
        // when
        dataManager.publish({
          subscription: subscriptions['LOAD_STATE_DEFINITIONS'],
          state: LOADING_STATE.LOADED,
          response: parsedDiagram
        });

        await flushPromises();
        node.update();

        // expect invalid activityId to have been removed
        expect(pushMock).toHaveBeenCalled();
        const search = pushMock.mock.calls[0][0].search;
        const {
          version,
          workflow,
          activityId,
          ...rest
        } = mockFullFilterWithWorkflow;

        expect(parseQueryString(search).filter).toEqual(rest);
      });

      it('when the activityId in url is invalid', async () => {
        const node = mount(
          <InstancesContainerWrapped
            {...{dataManager}}
            {...mockLocalStorageProps}
            {...getRouterProps({
              ...mockFullFilterWithWorkflow,
              activityId: 'x'
            })}
          />
        );

        // when
        await flushPromises();
        node.update();

        // expect invalid activityId to have been removed
        expect(pushMock).toHaveBeenCalled();
        const search = pushMock.mock.calls[0][0].search;
        const {activityId, ...rest} = mockFullFilterWithWorkflow;
        expect(parseQueryString(search).filter).toEqual(rest);
      });

      it('should remove activityId when version="all"', async () => {
        const node = mount(
          <InstancesContainerWrapped
            {...{dataManager}}
            {...mockLocalStorageProps}
            {...getRouterProps({
              ...mockFullFilterWithWorkflow,
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
        const {activityId, version, ...rest} = mockFullFilterWithWorkflow;
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
            {...mockLocalStorageProps}
            {...getRouterProps({
              ...mockFullFilterWithoutWorkflow
            })}
          />
        );

        // when
        await flushPromises();
        node.update();

        // then
        const InstancesNode = node.find('Instances');
        expect(InstancesNode.prop('filter')).toEqual(
          decodeFields(mockFullFilterWithoutWorkflow)
        );
        expect(InstancesNode.prop('diagramModel')).toEqual({});
        expect(InstancesNode.prop('statistics')).toEqual([]);
        expect(InstancesNode.prop('firstElement')).toBe(DEFAULT_FIRST_ELEMENT);
        expect(InstancesNode.prop('sorting')).toEqual(DEFAULT_SORTING);
      });

      it('should update the state for a valid filter with version="all"', async () => {
        // given
        const {
          activityId,
          ...filterWithoutActivityId
        } = mockFullFilterWithWorkflow;
        const validFilterWithVersionAll = {
          ...filterWithoutActivityId,
          version: 'all'
        };
        const node = mount(
          <InstancesContainerWrapped
            {...{dataManager}}
            {...mockLocalStorageProps}
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
            {...mockLocalStorageProps}
            {...getRouterProps(mockFullFilterWithWorkflow)}
          />
        );
        await flushPromises();
        node.update();

        // when
        // change filter without chaning workflow
        const newFilter = {
          ...mockFullFilterWithWorkflow,
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
            {...mockLocalStorageProps}
            {...getRouterProps(mockFullFilterWithWorkflow)}
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
        const newFilter = {...mockFullFilterWithWorkflow, version: '3'};
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
          {...mockLocalStorageProps}
          {...getRouterProps(mockFullFilterWithoutWorkflow)}
        />
      );

      await flushPromises();
      node.update();
      const InstancesNode = node.find('Instances');

      // when
      InstancesNode.prop('onFilterChange')(mockFullFilterWithWorkflow);

      // then
      expect(pushMock).toHaveBeenCalled();
      expect(pushMock.mock.calls[0][0].search).toBe(
        filterUtils.getFilterQueryString({
          ...mockFullFilterWithoutWorkflow,
          ...mockFullFilterWithWorkflow,
          variable: ''
        })
      );
    });

    it('should handle filter reset', async () => {
      const node = mount(
        <InstancesContainerWrapped
          {...{dataManager}}
          {...mockLocalStorageProps}
          {...getRouterProps(mockFullFilterWithoutWorkflow)}
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
        filterUtils.getFilterQueryString(DEFAULT_FILTER)
      );
    });
  });

  describe('sorting', () => {
    it('should be able to handle sorting change', async () => {
      // given
      const node = mount(
        <InstancesContainerWrapped
          {...{dataManager}}
          {...mockLocalStorageProps}
          {...getRouterProps(mockFullFilterWithoutWorkflow)}
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
          {...mockLocalStorageProps}
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
          {...mockLocalStorageProps}
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
});
