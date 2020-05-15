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
import {InstanceSelectionProvider} from 'modules/contexts/InstanceSelectionContext';

import {formatGroupedWorkflows} from 'modules/utils/instance';
import {
  DEFAULT_FILTER,
  DEFAULT_SORTING,
  DEFAULT_FIRST_ELEMENT,
  DEFAULT_MAX_RESULTS,
  SORT_ORDER,
  INCIDENTS_FILTER,
  LOADING_STATE,
} from 'modules/constants';
import {
  mockResolvedAsyncFn,
  flushPromises,
  groupedWorkflowsMock,
  createMockInstancesObject,
} from 'modules/testUtils';
import {parsedDiagram} from 'modules/utils/bpmn';

import {
  mockFullFilterWithoutWorkflow,
  mockFullFilterWithWorkflow,
} from './InstancesContainer.setup';

import {createMockDataManager} from 'modules/testHelpers/dataManager';
import {DataManagerProvider} from 'modules/DataManager';
import PropTypes from 'prop-types';

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
      search: filterUtils.getFilterQueryString(filter, filter.workflow),
    },
  };
}

function ProviderWrapper(props) {
  return (
    <DataManagerProvider>
      <InstanceSelectionProvider>{props.children}</InstanceSelectionProvider>
    </DataManagerProvider>
  );
}
ProviderWrapper.propTypes = {
  children: PropTypes.oneOfType([
    PropTypes.arrayOf(PropTypes.node),
    PropTypes.node,
  ]),
};
describe('InstancesContainer', () => {
  let dataManager;

  beforeEach(() => {
    jest.clearAllMocks();
    jest.spyOn(localStorage, 'setItem');

    dataManager = createMockDataManager();
  });

  describe('mounting', () => {
    it('should fetch the groupedWorkflows', async () => {
      // given
      const node = mount(
        <ProviderWrapper>
          <InstancesContainerWrapped {...getRouterProps()} {...{dataManager}} />
        </ProviderWrapper>
      );

      // when
      await flushPromises();
      node.update();

      // then
      expect(api.fetchGroupedWorkflows).toHaveBeenCalled();
    });

    it('should fetch the workflow xml', async () => {
      const node = mount(
        <ProviderWrapper>
          <InstancesContainerWrapped
            {...getRouterProps(mockFullFilterWithWorkflow)}
            {...{dataManager}}
          />
        </ProviderWrapper>
      );

      dataManager.getWorkflowXML = jest.fn(() => {
        dataManager.subscriptions()['LOAD_STATE_DEFINITIONS']({
          state: LOADING_STATE.LOADED,
          response: parsedDiagram,
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
        <ProviderWrapper>
          <InstancesContainerWrapped
            {...{dataManager}}
            {...getRouterProps(mockFullFilterWithWorkflow)}
          />
        </ProviderWrapper>
      );

      // when
      await flushPromises();
      node.update();

      const subscriptions = dataManager.subscriptions();
      dataManager.publish({
        subscription: subscriptions['LOAD_STATE_DEFINITIONS'],
        state: LOADING_STATE.LOADED,
        response: parsedDiagram,
      });

      // then
      expect(dataManager.getWorkflowInstancesStatistics).toHaveBeenCalled();
    });
  });

  describe('fetching workflow instances with initially empty query', () => {
    const expectedQuery = {
      active: true,
      completed: true,
      canceled: true,
      incidents: true,
    };
    const initialFilterUrl = {};
    let node;
    beforeEach(() => {
      dataManager.getWorkflowInstances.mockClear();
      node = mount(
        <ProviderWrapper>
          <InstancesContainerWrapped
            {...{dataManager}}
            {...getRouterProps(initialFilterUrl)}
          />
        </ProviderWrapper>
      );
    });

    it('should call getWorkflowInstances one time with correct parameters', () => {
      expect(dataManager.getWorkflowInstances).toHaveBeenCalledTimes(1);
      expect(dataManager.getWorkflowInstances).toHaveBeenCalledWith(
        expect.objectContaining({})
      );
    });

    it('should call getWorkflowInstances for second time with correct parameters when filter is changed', () => {
      // given

      // when
      node.find('Instances').prop('onFilterChange')(expectedQuery);

      // then
      expect(dataManager.getWorkflowInstances).toHaveBeenCalledTimes(2);
      expect(dataManager.getWorkflowInstances).toHaveBeenNthCalledWith(
        2,
        expect.objectContaining({
          ...expectedQuery,
        })
      );
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
      workflowIds: ['1'],
    };
    let node;
    beforeEach(() => {
      dataManager.getWorkflowInstances.mockClear();
      node = mount(
        <ProviderWrapper>
          <InstancesContainerWrapped
            {...{dataManager}}
            {...getRouterProps(mockFullFilterWithWorkflow)}
          />
        </ProviderWrapper>
      );

      dataManager.getWorkflowInstances = jest.fn(() => {
        dataManager.subscriptions()['LOAD_LIST_INSTANCES']({
          state: LOADING_STATE.LOADED,
          response: expectedQuery,
        });
      });
    });

    it('should fetch instances one time with correct parameters', () => {
      expect(dataManager.getWorkflowInstances).toHaveBeenCalledTimes(1);

      expect(dataManager.getWorkflowInstances).toHaveBeenCalledWith(
        expect.objectContaining(expectedQuery)
      );
    });

    it('should call getWorkflowInstances again with correct parameters when filter is first set empty, and then set again', () => {
      // given
      const emptyFilters = {};

      // when
      node.find('Instances').prop('onFilterChange')(emptyFilters);

      node = mount(
        <ProviderWrapper>
          <InstancesContainerWrapped
            {...{dataManager}}
            {...getRouterProps(emptyFilters)}
          />
        </ProviderWrapper>
      );

      // then
      // getWorkflowInstances called for a second time with empty filters
      expect(dataManager.getWorkflowInstances).toHaveBeenCalledTimes(2);
      expect(dataManager.getWorkflowInstances).toHaveBeenNthCalledWith(
        2,
        expect.objectContaining(emptyFilters)
      );

      // set some filters
      node.find('Instances').prop('onFilterChange')({
        active: true,
        incidents: true,
      });

      // mount again to change router props (for filter url change)
      node = mount(
        <ProviderWrapper>
          <InstancesContainerWrapped
            {...{dataManager}}
            {...getRouterProps({
              active: true,
              incidents: true,
            })}
          />
        </ProviderWrapper>
      );

      // getWorkflowInstances called for a third time with correct filters
      expect(dataManager.getWorkflowInstances).toHaveBeenCalledTimes(3);
      expect(dataManager.getWorkflowInstances).toHaveBeenNthCalledWith(
        3,
        expect.objectContaining({
          active: true,
          incidents: true,
          running: true,
        })
      );
    });

    it('should fetch instances when filter changes', async () => {
      // given

      // when
      await flushPromises();
      node.update();

      // thenw

      let instancesContainerNode = node.find(InstancesContainerWrapped);

      expect(dataManager.getWorkflowInstances).toHaveBeenCalled();
      const call = dataManager.getWorkflowInstances.mock.calls[0][0];
      expect(call).toMatchObject({
        ...expectedQuery,
      });
      expect(call.firstResult).toEqual(
        instancesContainerNode.state('firstElement')
      );
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
      expect(call).toMatchObject({
        ...expectedQuery,
      });
      expect(call.firstResult).toEqual(
        node.find(InstancesContainerWrapped).state('firstElement')
      );
      expect(call.maxResults).toEqual(DEFAULT_MAX_RESULTS);
    });

    it('should fetch instances when firstElement changes', async () => {
      // given
      const node = mount(
        <ProviderWrapper>
          <InstancesContainerWrapped
            {...{dataManager}}
            {...getRouterProps(mockFullFilterWithWorkflow)}
          />
        </ProviderWrapper>
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
      expect(call).toMatchObject({
        ...expectedQuery,
      });
      expect(call.maxResults).toEqual(DEFAULT_MAX_RESULTS);
    });
  });

  it('should write the filter to local storage', async () => {
    // given
    const node = mount(
      <ProviderWrapper>
        <InstancesContainerWrapped {...{dataManager}} {...getRouterProps()} />
      </ProviderWrapper>
    );

    // when

    node
      .find(InstancesContainerWrapped)
      .setState({filter: {...DEFAULT_FILTER, running: false}});
    await flushPromises();
    node.update();

    // then
    expect(localStorage.setItem).toHaveBeenCalled();
  });

  it('should render the Instances', () => {
    const node = mount(
      <ProviderWrapper>
        <InstancesContainerWrapped {...{dataManager}} {...getRouterProps()} />
      </ProviderWrapper>
    );

    expect(node.find(Instances)).toExist();
  });

  it('should pass data to Instances for default filter', async () => {
    // given
    const node = mount(
      <ProviderWrapper>
        <InstancesContainerWrapped {...{dataManager}} {...getRouterProps()} />
      </ProviderWrapper>
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
      ...DEFAULT_FILTER,
    });
    expect(InstancesNode.props().diagramModel).toEqual({});
  });

  it('should pass data to Instances for full filter, without workflow data', async () => {
    // given
    const node = mount(
      <ProviderWrapper>
        <InstancesContainerWrapped
          {...{dataManager}}
          {...getRouterProps(decodeFields(mockFullFilterWithoutWorkflow))}
        />
      </ProviderWrapper>
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
      <ProviderWrapper>
        <InstancesContainerWrapped
          {...{dataManager}}
          {...getRouterProps(mockFullFilterWithWorkflow)}
        />
      </ProviderWrapper>
    );

    dataManager.getWorkflowXML = jest.fn(() => {
      dataManager.subscriptions()['LOAD_STATE_DEFINITIONS']({
        state: LOADING_STATE.LOADED,
        response: parsedDiagram,
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
      <ProviderWrapper>
        <InstancesContainerWrapped
          {...{dataManager}}
          {...getRouterProps({
            ...rest,
            version: 'all',
          })}
        />
      </ProviderWrapper>
    );

    // when
    await flushPromises();
    node.update();

    const InstancesNode = node.find(Instances);

    expect(InstancesNode.prop('filter')).toEqual(
      decodeFields({
        ...rest,
        version: 'all',
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
        <ProviderWrapper>
          <InstancesContainerWrapped {...{dataManager}} {...routerProps} />
        </ProviderWrapper>
      );

      let instancesContainerNode = node.find(InstancesContainerWrapped);
      const setFilterFromUrlSpy = jest.spyOn(
        instancesContainerNode.instance(),
        'setFilterInURL'
      );

      await flushPromises();
      node.update();

      // when

      node.setProps({
        children: (
          <InstancesContainerWrapped {...{dataManager}} {...newRouterProps} />
        ),
      });

      node.update();

      // when componentDidUpdate & Url changed
      expect(setFilterFromUrlSpy).toHaveBeenCalledTimes(1);
      expect(setFilterFromUrlSpy).toHaveBeenCalledWith(
        mockFullFilterWithWorkflow,
        'New demo process'
      );
    });

    describe('fixing an invalid filter in url', () => {
      it('should add the default filter to the url when no filter is present', async () => {
        const noFilterRouterProps = {
          history: {push: jest.fn(), location: ''},
          location: {
            search: '',
          },
        };

        mount(
          <ProviderWrapper>
            <InstancesContainerWrapped
              {...{dataManager}}
              {...noFilterRouterProps}
            />
          </ProviderWrapper>
        );

        // when
        await flushPromises();

        // then
        expect(noFilterRouterProps.history.push).toHaveBeenCalled();
        expect(noFilterRouterProps.history.push.mock.calls[0][0].search).toBe(
          '?filter={"active":true,"incidents":true}'
        );
      });

      it('when a value in the filter in url is invalid', async () => {
        const invalidFilterRouterProps = {
          history: {push: jest.fn(), location: ''},
          location: {
            search:
              '?filter={"active": fallse, "errorMessage": "No%20data%20found%20for%20query%20$.foo."',
          },
        };
        const node = mount(
          <ProviderWrapper>
            <InstancesContainerWrapped
              {...{dataManager}}
              {...invalidFilterRouterProps}
            />
          </ProviderWrapper>
        );

        // when
        await flushPromises();
        node.update();

        // then
        const encodedFilter = '{"active":true,"incidents":true}';

        expect(invalidFilterRouterProps.history.push).toHaveBeenCalled();
        expect(
          invalidFilterRouterProps.history.push.mock.calls[0][0].search
        ).toBe(`?filter=${encodedFilter}`);
      });

      it('when the workflow in url is invalid', async () => {
        const node = mount(
          <ProviderWrapper>
            <InstancesContainerWrapped
              {...{dataManager}}
              {...getRouterProps({
                ...mockFullFilterWithWorkflow,
                workflow: 'x',
              })}
            />
          </ProviderWrapper>
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
          <ProviderWrapper>
            <InstancesContainerWrapped
              {...{dataManager}}
              {...getRouterProps({
                ...mockFullFilterWithWorkflow,
                version: 'x',
              })}
            />
          </ProviderWrapper>
        );
        const subscriptions = dataManager.subscriptions();
        // when
        dataManager.publish({
          subscription: subscriptions['LOAD_STATE_DEFINITIONS'],
          state: LOADING_STATE.LOADED,
          response: parsedDiagram,
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

      it('should remove activityId when version="all"', async () => {
        const node = mount(
          <ProviderWrapper>
            <InstancesContainerWrapped
              {...{dataManager}}
              {...getRouterProps({
                ...mockFullFilterWithWorkflow,
                version: 'all',
                activityId: 'taskD',
              })}
            />
          </ProviderWrapper>
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
          version: 'all',
        });
      });
    });

    describe('updating the state when filter is valid', () => {
      it('should update the state for a valid filter with no workflow', async () => {
        const node = mount(
          <ProviderWrapper>
            <InstancesContainerWrapped
              {...{dataManager}}
              {...getRouterProps({
                ...mockFullFilterWithoutWorkflow,
              })}
            />
          </ProviderWrapper>
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
          version: 'all',
        };
        const node = mount(
          <ProviderWrapper>
            <InstancesContainerWrapped
              {...{dataManager}}
              {...getRouterProps(validFilterWithVersionAll)}
            />
          </ProviderWrapper>
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
          <ProviderWrapper>
            <InstancesContainerWrapped
              {...{dataManager}}
              {...getRouterProps(mockFullFilterWithWorkflow)}
            />
          </ProviderWrapper>
        );
        await flushPromises();
        node.update();

        // when
        // change filter without chaning workflow
        const newFilter = {
          ...mockFullFilterWithWorkflow,
          endDate: '1955-12-28',
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
          <ProviderWrapper>
            <InstancesContainerWrapped
              {...{dataManager}}
              {...getRouterProps(mockFullFilterWithWorkflow)}
            />
          </ProviderWrapper>
        );
        await flushPromises();
        node.update();

        dataManager.getWorkflowXML = jest.fn(() => {
          dataManager.subscriptions()['LOAD_STATE_DEFINITIONS']({
            state: LOADING_STATE.LOADED,
            response: parsedDiagram,
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
        <ProviderWrapper>
          <InstancesContainerWrapped
            {...{dataManager}}
            {...getRouterProps(mockFullFilterWithoutWorkflow)}
          />
        </ProviderWrapper>
      );

      await flushPromises();
      node.update();
      const InstancesNode = node.find('Instances');

      // when
      InstancesNode.prop('onFilterChange')(mockFullFilterWithWorkflow);

      // then
      expect(pushMock).toHaveBeenCalled();
      expect(pushMock.mock.calls[0][0].search).toBe(
        filterUtils.getFilterQueryString(
          {
            ...mockFullFilterWithoutWorkflow,
            ...mockFullFilterWithWorkflow,
            variable: '',
          },
          'New demo process'
        )
      );
    });

    it('should handle filter reset', async () => {
      const node = mount(
        <ProviderWrapper>
          <InstancesContainerWrapped
            {...{dataManager}}
            {...getRouterProps(mockFullFilterWithoutWorkflow)}
          />
        </ProviderWrapper>
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
        <ProviderWrapper>
          <InstancesContainerWrapped
            {...{dataManager}}
            {...getRouterProps(mockFullFilterWithoutWorkflow)}
          />
        </ProviderWrapper>
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
        <ProviderWrapper>
          <InstancesContainerWrapped
            {...{dataManager}}
            {...getRouterProps(INCIDENTS_FILTER)}
          />
        </ProviderWrapper>
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
        <ProviderWrapper>
          <InstancesContainerWrapped
            {...{dataManager}}
            {...getRouterProps(filterWithCompleted)}
          />
        </ProviderWrapper>
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

    it('should update state correctly on instances click', async () => {
      // given
      const node = mount(
        <InstancesContainerWrapped {...{dataManager}} {...getRouterProps({})} />
      );
      await flushPromises();
      node.update();

      // when

      node.find('Instances').prop('onInstancesClick')('test');
      await flushPromises();
      node.update();

      // then
      expect(node.state().resetFilters).toEqual(true);
      expect(node.state().operationId).toEqual('test');
    });
  });
});
