/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';
import {HashRouter as Router} from 'react-router-dom';
import {DataManager} from 'modules/DataManager/core';

import Dropdown from 'modules/components/Dropdown';
import Header from './Header';
import Badge from 'modules/components/Badge';

import {CollapsablePanelProvider} from 'modules/contexts/CollapsablePanelContext';
import {ThemeProvider} from 'modules/contexts/ThemeContext';

import * as api from 'modules/api/header/header';
import * as instancesApi from 'modules/api/instances/instances';

import {flushPromises, mockResolvedAsyncFn} from 'modules/testUtils';
import {DEFAULT_FILTER, FILTER_SELECTION} from 'modules/constants';
import {getFilterQueryString} from 'modules/utils/filter';

import * as Styled from './styled.js';

import {LOADING_STATE} from 'modules/constants';

jest.mock('bpmn-js', () => ({}));
jest.mock('modules/utils/bpmn');
jest.mock('modules/DataManager/core');

const USER = {
  user: {
    firstname: 'Jonny',
    lastname: 'Prosciutto',
    canLogout: true
  }
};

const RUNNING_COUNT = 23;
const ACTIVE_COUNT = 10;
const INCIDENTS_COUNT = 13;

// component mocks
// avoid loop of redirects when testing handleLogout
jest.mock(
  'react-router-dom/Redirect',
  () =>
    function Redirect() {
      return <div />;
    }
);

// props mocks
const mockCollapsablePanelProps = {
  getStateLocally: jest.fn(),
  isFiltersCollapsed: false,
  isSelectionsCollapsed: false,
  expandFilters: jest.fn(),
  expandSelections: jest.fn()
};

DataManager.mockImplementation(() => {
  return {
    publish: jest.fn(({subscription, state, response}) =>
      subscription({state, response})
    ),
    subscribe: jest.fn(),
    getWorkflowCoreStatistics: jest.fn()
  };
});

// api mocks
api.fetchUser = mockResolvedAsyncFn(USER);
api.logout = mockResolvedAsyncFn();
instancesApi.fetchWorkflowCoreStatistics = mockResolvedAsyncFn({
  data: {
    running: RUNNING_COUNT,
    active: ACTIVE_COUNT,
    withIncidents: INCIDENTS_COUNT
  }
});

const mountComponent = props => {
  const node = mount(
    <Router>
      <ThemeProvider>
        <CollapsablePanelProvider>
          <Header.WrappedComponent {...props} />
        </CollapsablePanelProvider>
      </ThemeProvider>
    </Router>
  );
  return node;
};

const mockValues = {
  filter: {foo: 'bar'},
  filterCount: 1,
  selectionCount: 2,
  instancesInSelectionsCount: 3
};

describe('Header', () => {
  let dataManager;

  beforeEach(() => {
    dataManager = new DataManager();
    api.fetchUser.mockClear();
    instancesApi.fetchWorkflowCoreStatistics.mockClear();
  });

  describe('localState values', () => {
    it('should render the correct links', async () => {
      dataManager.getWorkflowCoreStatistics.mockClear();

      const mockProps = {
        ...mockValues,
        ...mockCollapsablePanelProps,
        dataManager,
        getStateLocally: () => ({})
      };

      const node = mountComponent(mockProps);
      const subscriptions = node.find(Header.WrappedComponent).instance()
        .subscriptions;

      dataManager.publish({
        subscription: subscriptions['LOAD_CORE_STATS'],
        state: LOADING_STATE.LOADED,
        response: {
          coreStatistics: {
            running: RUNNING_COUNT,
            active: ACTIVE_COUNT,
            withIncidents: INCIDENTS_COUNT
          }
        }
      });

      // when;
      await flushPromises();
      node.update();

      expect(dataManager.getWorkflowCoreStatistics).toHaveBeenCalled();

      expect(node.find(Styled.Menu)).toExist();
      expect(node.find(Styled.Menu).props().role).toBe('navigation');

      expect(node.find(Styled.Menu).find('li').length).toBe(6);

      const BrandLinkNode = node.find('[data-test="header-link-brand"]');
      expect(BrandLinkNode).toExist();
      expect(BrandLinkNode.text()).toContain('Camunda Operate');
      expect(BrandLinkNode.find(Styled.LogoIcon)).toExist();

      const DashboardLinkNode = node.find(
        '[data-test="header-link-dashboard"]'
      );
      expect(DashboardLinkNode).toExist();
      expect(DashboardLinkNode.text()).toContain('Dashboard');

      const InstancesLinkNode = node.find(
        '[data-test="header-link-instances"]'
      );
      expect(InstancesLinkNode).toExist();
      expect(InstancesLinkNode.text()).toContain('Running Instances');

      expect(InstancesLinkNode.find(Badge).text()).toBe(
        RUNNING_COUNT.toString()
      );
      expect(InstancesLinkNode.find(Badge).props().type).toBe(
        'RUNNING_INSTANCES'
      );

      const FiltersLinkNode = node.find('[data-test="header-link-filters"]');
      expect(FiltersLinkNode).toExist();
      expect(FiltersLinkNode.text()).toContain('Filters');
      expect(FiltersLinkNode.find(Badge).text()).toBe(
        mockValues.filterCount.toString()
      );
      expect(FiltersLinkNode.find(Badge).props().type).toBe('FILTERS');

      const IncidentsLinkNode = node.find(
        '[data-test="header-link-incidents"]'
      );
      expect(IncidentsLinkNode).toExist();
      expect(IncidentsLinkNode.text()).toContain('Incidents');
      expect(IncidentsLinkNode.find(Badge).text()).toBe(
        INCIDENTS_COUNT.toString().toString()
      );
      expect(IncidentsLinkNode.find(Badge).props().type).toBe('INCIDENTS');

      const SelectionsLinkNode = node.find(
        '[data-test="header-link-selections"]'
      );
      expect(SelectionsLinkNode).toExist();
      expect(SelectionsLinkNode.text()).toContain('Selections');
      expect(SelectionsLinkNode.find(Badge).length).toBe(2);
      expect(
        SelectionsLinkNode.find(Badge)
          .at(0)
          .text()
      ).toBe(mockValues.selectionCount.toString());
      expect(
        SelectionsLinkNode.find(Badge)
          .at(1)
          .text()
      ).toBe(mockValues.instancesInSelectionsCount.toString());
    });

    it("should get the filterCount, selectionCount & instancesInSelectionsCount from props if it's provided", () => {
      const mockProps = {
        ...mockValues,
        ...mockCollapsablePanelProps,
        dataManager,
        getStateLocally: () => ({})
      };

      const node = mountComponent(mockProps);

      // then
      expect(
        node
          .find('[data-test="header-link-filters"]')
          .find('Badge')
          .text()
      ).toEqual(mockValues.filterCount.toString());
      expect(
        node
          .find('[data-test="header-link-selections"]')
          .find('Badge')
          .at(0)
          .text()
      ).toEqual(mockValues.selectionCount.toString());
      expect(
        node
          .find('[data-test="header-link-selections"]')
          .find('Badge')
          .at(1)
          .text()
      ).toEqual(mockValues.instancesInSelectionsCount.toString());
    });

    it('it should add default filter if no filter read from localStorage', async () => {
      // given
      const mockProps = {
        ...mockCollapsablePanelProps,
        dataManager,
        getStateLocally: () => {
          return {selectionCount: 2, instancesInSelectionsCount: 3};
        }
      };

      const node = mountComponent(mockProps);

      const subscriptions = node.find(Header.WrappedComponent).instance()
        .subscriptions;

      dataManager.publish({
        subscription: subscriptions['LOAD_CORE_STATS'],
        state: LOADING_STATE.LOADED,
        response: {
          coreStatistics: {
            running: RUNNING_COUNT,
            active: ACTIVE_COUNT,
            withIncidents: INCIDENTS_COUNT
          }
        }
      });

      await flushPromises();
      node.update();

      // then
      const encodedFilter = encodeURIComponent(
        '{"active":true,"incidents":true}'
      );
      expect(
        node
          .find('[data-test="header-link-filters"]')
          .childAt(0)
          .props().to
      ).toEqual(`/instances?filter=${encodedFilter}`);
      expect(
        node
          .find('[data-test="header-link-filters"]')
          .find(Badge)
          .text()
      ).toEqual(RUNNING_COUNT.toString());
    });

    it("should get filterCount, selectionCount & instancesInSelectionsCount from localState if it's not provided by the props", async () => {
      // given
      const mockProps = {
        ...mockCollapsablePanelProps,
        dataManager,
        getStateLocally: () => mockValues
      };
      const node = mountComponent(mockProps);

      await flushPromises();
      node.update();

      // then
      expect(
        node
          .find('[data-test="header-link-filters"]')
          .find('Badge')
          .text()
      ).toEqual(mockValues.filterCount.toString());
      expect(
        node
          .find('[data-test="header-link-selections"]')
          .find('Badge')
          .at(0)
          .text()
      ).toEqual(mockValues.selectionCount.toString());
      expect(
        node
          .find('[data-test="header-link-selections"]')
          .find('Badge')
          .at(1)
          .text()
      ).toEqual(mockValues.instancesInSelectionsCount.toString());
    });
  });

  describe('api values', () => {
    it("should get the value from props if it's provided", async () => {
      const mockApiProps = {
        runningInstancesCount: 1,
        incidentsCount: 2
      };
      const mockProps = {
        getStateLocally: () => {},
        ...mockCollapsablePanelProps,
        ...mockValues,
        dataManager
      };

      const node = mountComponent(mockProps);

      const subscriptions = node.find(Header.WrappedComponent).instance()
        .subscriptions;

      dataManager.publish({
        subscription: subscriptions['LOAD_CORE_STATS'],
        state: LOADING_STATE.LOADED,
        response: {
          coreStatistics: {
            running: mockApiProps.runningInstancesCount,
            active: ACTIVE_COUNT,
            withIncidents: mockApiProps.incidentsCount
          }
        }
      });

      await flushPromises();
      node.update();
      // then
      expect(
        node
          .find('[data-test="header-link-instances"]')
          .find('Badge')
          .text()
      ).toEqual(mockApiProps.runningInstancesCount.toString());
      expect(
        node
          .find('[data-test="header-link-incidents"]')
          .find('Badge')
          .text()
      ).toEqual(mockApiProps.incidentsCount.toString());
    });

    it("should get the value from api if it's not in the props", async () => {
      // given
      const mockProps = {
        getStateLocally: () => {},
        ...mockCollapsablePanelProps,
        ...mockValues,
        dataManager
      };

      const node = mountComponent(mockProps);

      const subscriptions = node.find(Header.WrappedComponent).instance()
        .subscriptions;

      dataManager.publish({
        subscription: subscriptions['LOAD_CORE_STATS'],
        state: LOADING_STATE.LOADED,
        response: {
          coreStatistics: {
            running: RUNNING_COUNT,
            active: ACTIVE_COUNT,
            withIncidents: INCIDENTS_COUNT
          }
        }
      });

      // when
      await flushPromises();
      node.update();

      // then
      expect(dataManager.getWorkflowCoreStatistics).toBeCalled();

      // then
      expect(
        node
          .find('[data-test="header-link-instances"]')
          .find('Badge')
          .text()
      ).toEqual(RUNNING_COUNT.toString());
      expect(
        node
          .find('[data-test="header-link-incidents"]')
          .find('Badge')
          .text()
      ).toEqual(INCIDENTS_COUNT.toString());
    });

    it('should show zero numbers when fetch error occured', async () => {
      // given
      const mockProps = {
        getStateLocally: () => {},
        ...mockCollapsablePanelProps,
        ...mockValues,
        dataManager
      };

      const node = mountComponent(mockProps);

      const subscriptions = node.find(Header.WrappedComponent).instance()
        .subscriptions;

      dataManager.publish({
        subscription: subscriptions['LOAD_CORE_STATS'],
        state: LOADING_STATE.LOAD_FAILED,
        error: new Error('error')
      });

      // when
      await flushPromises();
      node.update();

      // then
      expect(dataManager.getWorkflowCoreStatistics).toBeCalled();
      expect(
        node
          .find('[data-test="header-link-incidents"]')
          .find('Badge')
          .text()
      ).toEqual('0');
      expect(
        node
          .find('[data-test="header-link-instances"]')
          .find('Badge')
          .text()
      ).toEqual('0');
    });
  });

  describe('links', () => {
    it('should add the correct url to links', () => {
      const props = {
        ...mockCollapsablePanelProps,
        ...mockValues,
        dataManager
      };
      const node = mountComponent(props);

      // running instances
      expect(
        node
          .find('[data-test="header-link-instances"]')
          .find(Styled.ListLink)
          .props().to
      ).toBe('/instances' + getFilterQueryString(FILTER_SELECTION.running));

      // filter

      expect(
        node
          .find('[data-test="header-link-filters"]')
          .find(Styled.ListLink)
          .props().to
      ).toBe('/instances' + getFilterQueryString(mockValues.filter));

      // incidents

      expect(
        node
          .find('[data-test="header-link-incidents"]')
          .find(Styled.ListLink)
          .props().to
      ).toBe('/instances' + getFilterQueryString({incidents: true}));
    });

    it('should not link anywhere when onFilterReset method is passed', () => {
      const onFilterResetMock = jest.fn();
      const emptyRoute = ' ';

      const mockProps = {
        ...mockCollapsablePanelProps,
        active: 'instances',
        getStateLocally: () => ({}),
        onFilterReset: onFilterResetMock,
        dataManager
      };

      const node = mountComponent(mockProps);
      let instancesLinkNode = node
        .find('[data-test="header-link-instances"]')
        .find(Styled.ListLink);
      let filterLinkNode = node
        .find('[data-test="header-link-filters"]')
        .find(Styled.ListLink);
      let incidentsLinkNode = node
        .find('[data-test="header-link-incidents"]')
        .find(Styled.ListLink);

      expect(instancesLinkNode.props().to).toBe(emptyRoute);
      expect(filterLinkNode.props().to).toBe(emptyRoute);
      expect(incidentsLinkNode.props().to).toBe(emptyRoute);
    });

    it('should update the filter state directly', () => {
      const onFilterResetMock = jest.fn();

      const mockProps = {
        ...mockCollapsablePanelProps,
        active: 'instances',
        getStateLocally: () => ({}),
        onFilterReset: onFilterResetMock,
        dataManager
      };

      const node = mountComponent(mockProps);

      let instancesLinkNode = node
        .find('[data-test="header-link-instances"]')
        .find(Styled.ListLink);
      let incidentsLinkNode = node
        .find('[data-test="header-link-incidents"]')
        .find(Styled.ListLink);
      let filterLinkNode = node
        .find('[data-test="header-link-filters"]')
        .find(Styled.ListLink);

      filterLinkNode.simulate('click');
      expect(onFilterResetMock).toHaveBeenCalledWith({});

      instancesLinkNode.simulate('click');
      expect(onFilterResetMock).toHaveBeenCalledWith({
        active: true,
        incidents: true
      });

      incidentsLinkNode.simulate('click');
      expect(onFilterResetMock).toHaveBeenCalledWith({
        incidents: true
      });
    });

    describe('highlighting', () => {
      it('should highlight the dashboard link when active="dashboard"', () => {
        const mockProps = {
          ...mockCollapsablePanelProps,
          active: 'dashboard',
          getStateLocally: () => ({}),
          dataManager
        };
        const node = mountComponent(mockProps);
        let dashboardNode = node.find('[data-test="header-link-dashboard"]');

        // then
        expect(dashboardNode.childAt(0).prop('isActive')).toBe(true);
      });

      it('should not highlight the dashboard link when active!="dashboard"', () => {
        const mockProps = {
          ...mockCollapsablePanelProps,
          active: 'instances',
          getStateLocally: () => ({}),
          dataManager
        };

        const node = mountComponent(mockProps);

        let dashboardNode = node.find('[data-test="header-link-dashboard"]');

        // then
        expect(dashboardNode.childAt(0).prop('isActive')).toBe(false);
      });

      it('should highlight filters link when filters is not collapsed', () => {
        // given
        const mockProps = {
          ...mockCollapsablePanelProps,
          active: 'instances',
          isFiltersCollapsed: false,
          getStateLocally: () => ({}),
          dataManager
        };

        const node = mountComponent(mockProps);

        let filtersNode = node.find('[data-test="header-link-filters"]');

        // then
        expect(filtersNode.childAt(0).prop('isActive')).toBe(true);
      });

      it('should not highlight filters link when filters is collapsed', async () => {
        // given
        // we mount Header.WrappedComponent as we need to overwrite the value of
        // isFiltersCollapsed from CollapsablePanelProvider
        const mockProps = {
          ...mockCollapsablePanelProps,
          ...mockValues,
          active: 'instances',
          isFiltersCollapsed: true,
          dataManager
        };

        const node = mountComponent(mockProps);

        // when
        await flushPromises();
        node.update();

        let filtersNode = node.find('[data-test="header-link-filters"]');

        // then
        expect(filtersNode.childAt(0).prop('isActive')).toBe(false);
      });

      it('should highlight selections link when selections is not collapsed', () => {
        // (1) when selections is not collapsed
        // given
        const mockProps = {
          ...mockCollapsablePanelProps,
          isSelectionsCollapsed: false,
          active: 'instances',
          getStateLocally: () => mockValues,
          dataManager
        };
        const node = mountComponent(mockProps);
        let selectionsNode = node.find('[data-test="header-link-selections"]');

        // then
        expect(selectionsNode.childAt(0).prop('isActive')).toBe(true);
      });

      it('should not highlight selections link when selections is collapsed', () => {
        // given
        const mockProps = {
          ...mockCollapsablePanelProps,
          isSelectionsCollapsed: true,
          active: 'instances',
          getStateLocally: () => mockValues,
          dataManager
        };
        const node = mountComponent(mockProps);

        let selectionsNode = node.find('[data-test="header-link-selections"]');

        // then
        expect(selectionsNode.childAt(0).prop('isActive')).toBe(false);
      });

      it('should highlight running instance link when the filter equals the DEFAULT_FILTER', async () => {
        // given
        const mockProps = {
          ...mockCollapsablePanelProps,
          getStateLocally: () => ({}),
          active: 'instances',
          filter: DEFAULT_FILTER,
          dataManager
        };
        const node = mountComponent(mockProps);

        // when
        await flushPromises();
        node.update();

        let instancesNode = node.find('[data-test="header-link-instances"]');

        // then
        expect(instancesNode.childAt(0).prop('isActive')).toBe(true);
      });

      it('should not highlight running instance link when the filter !== DEFAULT_FILTER', async () => {
        // given
        const mockProps = {
          ...mockCollapsablePanelProps,
          getStateLocally: () => ({}),
          active: 'instances',
          filter: {incident: true},
          dataManager
        };
        const node = mountComponent(mockProps);

        // when
        await flushPromises();
        node.update();

        let instancesNode = node.find('[data-test="header-link-instances"]');

        // then
        expect(instancesNode.childAt(0).prop('isActive')).toBe(false);
      });

      it('should highlight incidents link when the filter equals incidents', async () => {
        // given
        const mockProps = {
          ...mockCollapsablePanelProps,
          ...mockValues,
          filter: {incidents: true},
          active: 'instances',
          dataManager
        };
        const node = mountComponent(mockProps);

        // when
        await flushPromises();
        node.update();

        let incidentsNode = node.find('[data-test="header-link-incidents"]');

        // then
        expect(incidentsNode.childAt(0).prop('isActive')).toBe(true);
      });

      it('should highlight incidents link when the filter not equals incidents', async () => {
        // given
        const mockProps = {
          ...mockCollapsablePanelProps,
          getStateLocally: () => ({}),
          filter: DEFAULT_FILTER,
          dataManager
        };
        const node = mountComponent(mockProps);

        // when
        await flushPromises();
        node.update();

        let incidentsNode = node.find('[data-test="header-link-incidents"]');

        // then
        expect(incidentsNode.childAt(0).prop('isActive')).toBe(false);
      });
    });
  });

  describe('detail', () => {
    it('should render the provided detail', () => {
      const mockProps = {
        getStateLocally: () => {},
        ...mockCollapsablePanelProps,
        ...mockValues,
        detail: <div data-test="header-detail">Detail</div>,
        dataManager
      };

      const node = mountComponent(mockProps);

      expect(node.find('[data-test="header-detail"]')).toExist();
    });
  });

  describe.only('Userarea', () => {
    it('it should request user information', async () => {
      const mockProps = {
        ...mockCollapsablePanelProps,
        getStateLocally: () => ({}),
        filter: DEFAULT_FILTER,
        dataManager
      };

      const node = mountComponent(mockProps);

      await flushPromises();
      node.update();

      expect(api.fetchUser).toHaveBeenCalled();
    });

    it('it should display user firstname and lastname', async () => {
      const mockProps = {
        ...mockCollapsablePanelProps,
        getStateLocally: () => ({}),
        dataManager
      };
      const node = mountComponent(mockProps);

      // await user data fetching
      await flushPromises();
      node.update();

      // check user firstname and lastname are shown in the Header
      const DropdownLabel = node.find('Dropdown').prop('label');
      expect(DropdownLabel).toContain(USER.firstname);
      expect(DropdownLabel).toContain(USER.lastname);
    });

    // id fails, can't access Dropdown.Option, is inside option
    it('should logout the user when calling handleLogout', async () => {
      api.logout = mockResolvedAsyncFn();
      const mockProps = {
        ...mockCollapsablePanelProps,
        getStateLocally: () => ({}),
        dataManager
      };
      const node = mountComponent(mockProps);

      // await user data fetching
      await flushPromises();
      node.update();
      node.find(Dropdown).simulate('click');

      node.update();
    });

    it('assign handleLogout as a Dropdown.Option onClick', async () => {
      const mockProps = {
        ...mockCollapsablePanelProps,
        getStateLocally: () => ({}),
        dataManager
      };
      const node = mountComponent(mockProps);

      //when
      node.find('button[data-test="dropdown-toggle"]').simulate('click');
      node.update();

      const onClick = node.find('[data-test="logout-button"]').prop('onClick');
      await onClick();
      node.update();

      expect(api.logout).toHaveBeenCalled();
    });
  });
});
