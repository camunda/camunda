/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';
import {HashRouter as Router} from 'react-router-dom';

import {createMockDataManager} from 'modules/testHelpers/dataManager';

import {LinkElement} from './NavElements';

import Header from './Header';
import Badge from 'modules/components/Badge';

import {ThemeProvider} from 'modules/contexts/ThemeContext';

import * as Styled from './styled.js';

import {LOADING_STATE} from 'modules/constants';

import {
  countStore,
  location,
  countStoreWithCount,
  mockInstance
} from './Header.setup';

jest.mock('bpmn-js', () => ({}));
jest.mock('modules/utils/bpmn');

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
  getStateLocally: () => ({}),
  isFiltersCollapsed: false,
  isSelectionsCollapsed: false,
  expandFilters: jest.fn(),
  expandSelections: jest.fn()
};

const mountComponent = props => {
  const node = mount(
    <Router>
      <ThemeProvider>
        <Header.WrappedComponent {...props} />
      </ThemeProvider>
    </Router>
  );
  return node;
};

describe('Header', () => {
  let dataManager;
  let node;
  let header;

  beforeEach(() => {
    dataManager = createMockDataManager();
  });

  describe('display', () => {
    it('should render all nav elements', () => {
      const mockProps = {
        location: location.dashboard,
        countStore,
        dataManager,
        ...mockCollapsablePanelProps
      };

      node = mountComponent(mockProps);
      header = node.find('Header');

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

      expect(InstancesLinkNode.find(Badge).props().type).toBe(
        'RUNNING_INSTANCES'
      );

      const FiltersLinkNode = node.find('[data-test="header-link-filters"]');
      expect(FiltersLinkNode).toExist();
      expect(FiltersLinkNode.text()).toContain('Filters');

      expect(FiltersLinkNode.find(Badge).props().type).toBe('FILTERS');

      const IncidentsLinkNode = node.find(
        '[data-test="header-link-incidents"]'
      );
      expect(IncidentsLinkNode).toExist();
      expect(IncidentsLinkNode.text()).toContain('Incidents');
      expect(IncidentsLinkNode.find(Badge).props().type).toBe('INCIDENTS');

      const SelectionsLinkNode = node.find(
        '[data-test="header-link-selections"]'
      );
      expect(SelectionsLinkNode).toExist();
      expect(SelectionsLinkNode.text()).toContain('Selections');
      expect(SelectionsLinkNode.find(Badge).length).toBe(2);
    });

    it('should render skeletons', () => {
      const mockProps = {
        location: location.dashboard,
        countStore,
        dataManager,
        ...mockCollapsablePanelProps
      };

      node = mountComponent(mockProps);
      header = node.find('Header');
      const skeletonBadge = '';
      const InstancesLinkNode = node.find(
        '[data-test="header-link-instances"]'
      );
      const FiltersLinkNode = node.find('[data-test="header-link-filters"]');
      const IncidentsLinkNode = node.find(
        '[data-test="header-link-incidents"]'
      );
      const SelectionsLinkNode = node.find(
        '[data-test="header-link-selections"]'
      );

      expect(InstancesLinkNode.find(Badge).text()).toBe(skeletonBadge);
      expect(FiltersLinkNode.find(Badge).text()).toBe(skeletonBadge);
      expect(IncidentsLinkNode.find(Badge).text()).toBe('');
      expect(
        SelectionsLinkNode.find(Badge)
          .at(0)
          .text()
      ).toBe('');
      expect(
        SelectionsLinkNode.find(Badge)
          .at(1)
          .text()
      ).toBe('');
    });

    it('should render values', () => {
      const mockProps = {
        location: location.dashboard,
        countStore: countStoreWithCount,
        dataManager,
        ...mockCollapsablePanelProps
      };

      node = mountComponent(mockProps);

      node.update();

      header = node.find('Header');

      const InstancesLinkNode = node.find(
        '[data-test="header-link-instances"]'
      );
      const FiltersLinkNode = node.find('[data-test="header-link-filters"]');
      const IncidentsLinkNode = node.find(
        '[data-test="header-link-incidents"]'
      );
      const SelectionsLinkNode = node.find(
        '[data-test="header-link-selections"]'
      );

      expect(InstancesLinkNode.find(Badge).text()).toBe(
        countStoreWithCount.running.toString()
      );
      expect(FiltersLinkNode.find(Badge).text()).toBe(
        countStoreWithCount.running.toString()
      );
      expect(IncidentsLinkNode.find(Badge).text()).toBe(
        countStoreWithCount.withIncidents.toString()
      );
      expect(
        SelectionsLinkNode.find(Badge)
          .at(0)
          .text()
      ).toBe(countStoreWithCount.selectionCount.toString());
      expect(
        SelectionsLinkNode.find(Badge)
          .at(1)
          .text()
      ).toBe(countStoreWithCount.instancesInSelectionsCount.toString());
    });

    it('should render user element', () => {
      const mockProps = {
        location: location.dashboard,
        countStore,
        dataManager,
        ...mockCollapsablePanelProps
      };

      node = mountComponent(mockProps);
      header = node.find('Header');

      expect(header.find('User'));
    });

    it('should render instance details skeleton on instance view', () => {
      const mockProps = {
        location: location.instance,
        countStore: countStoreWithCount,
        dataManager,
        ...mockCollapsablePanelProps
      };

      node = mountComponent(mockProps);
      expect(node.find('InstanceDetail')).not.toExist();
      expect(node.find(Styled.SkeletonBlock)).toExist();
    });
    it('should render instance details when on instance view', () => {
      const mockProps = {
        location: location.instance,
        countStore: countStoreWithCount,
        dataManager,
        ...mockCollapsablePanelProps
      };

      node = mountComponent(mockProps);

      const subscriptions = node.find(Header.WrappedComponent).instance()
        .subscriptions;

      dataManager.publish({
        subscription: subscriptions['LOAD_INSTANCE'],
        state: LOADING_STATE.LOADED,
        response: {
          ...mockInstance
        }
      });
      node.update();

      expect(node.find('InstanceDetail')).toExist();
      expect(node.find(Styled.SkeletonBlock)).not.toExist();
    });
  });

  describe('highlighting', () => {
    it('should highlight instances and filters', () => {
      const mockProps = {
        location: location.instances,
        countStore: countStoreWithCount,
        dataManager,
        ...mockCollapsablePanelProps
      };

      node = mountComponent(mockProps);

      expect(
        node
          .find('header')
          .find('[data-test="header-link-instances"]')
          .props().children.props.isActive
      ).toBe(true);

      expect(
        node
          .find('header')
          .find('[data-test="header-link-filters"]')
          .props().children.props.isActive
      ).toBe(true);
    });

    it('should highlight dashboard', () => {
      const mockProps = {
        location: location.dashboard,
        countStore: countStoreWithCount,
        dataManager,
        ...mockCollapsablePanelProps
      };

      node = mountComponent(mockProps);

      expect(
        node
          .find('header')
          .find(LinkElement)
          .props().isActive
      ).toBe(true);
    });
  });
});
