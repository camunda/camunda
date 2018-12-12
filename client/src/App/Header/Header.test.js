import React from 'react';
import {shallow} from 'enzyme';
import Dropdown from 'modules/components/Dropdown';
import Badge from 'modules/components/Badge';
import ComboBadge from 'modules/components/ComboBadge';
import * as api from 'modules/api/header/header';
import * as instancesApi from 'modules/api/instances/instances';
import {flushPromises, mockResolvedAsyncFn} from 'modules/testUtils';
import {FILTER_SELECTION} from 'modules/constants';

import HeaderWrapper from './Header';
import * as Styled from './styled';
import {localStateKeys, apiKeys, filtersMap} from './constants';

const {WrappedComponent: Header} = HeaderWrapper.WrappedComponent;

const USER = {
  user: {
    firstname: 'Jonny',
    lastname: 'Prosciutto'
  }
};

const INSTANCES_COUNT = 23;

api.fetchUser = mockResolvedAsyncFn(USER);
instancesApi.fetchWorkflowInstancesCount = mockResolvedAsyncFn(INSTANCES_COUNT);

const mockCollapsablePanelProps = {
  getStateLocally: jest.fn(),
  isFiltersCollapsed: false,
  isSelectionsCollapsed: false,
  expandFilters: jest.fn(),
  expandSelections: jest.fn()
};

describe('Header', () => {
  beforeEach(() => {
    api.fetchUser.mockClear();
    instancesApi.fetchWorkflowInstancesCount.mockClear();
  });

  describe('localState values', () => {
    const mockValues = {
      filter: {foo: 'bar'},
      filterCount: 1,
      selectionCount: 2,
      instancesInSelectionsCount: 3
    };
    it("should get the value from props if it's provided", () => {
      localStateKeys.forEach(key => {
        // given
        const mockProps = {
          ...mockCollapsablePanelProps,
          [key]: mockValues[key],
          getStateLocally: () => ({})
        };
        const node = shallow(<Header {...mockProps} />);

        // then
        expect(node.state(key)).toEqual(mockValues[key]);
      });
    });

    it("should get the value from localState if it's not in the props", () => {
      localStateKeys.forEach(key => {
        // given
        const mockLocalState = {[key]: mockValues[key]};
        const mockProps = {
          ...mockCollapsablePanelProps,
          getStateLocally: () => mockLocalState
        };
        const node = shallow(<Header {...mockProps} />);

        // then
        expect(node.state(key)).toEqual(mockValues[key]);
      });
    });
  });

  describe('api values', () => {
    it("should get the value from props if it's provided", () => {
      apiKeys.forEach(key => {
        // given
        const value = 23;
        const mockProps = {
          ...mockCollapsablePanelProps,
          [key]: value,
          getStateLocally: () => ({})
        };
        const node = shallow(<Header {...mockProps} />);

        // then
        expect(node.state(key)).toBe(value);
      });
    });

    it("should get the value from api if it's not in the props", async () => {
      // given
      const mockProps = {
        ...mockCollapsablePanelProps,
        getStateLocally: () => ({})
      };
      const node = shallow(<Header {...mockProps} />);
      // when
      await flushPromises();
      node.update();
      apiKeys.forEach(async key => {
        // then
        expect(instancesApi.fetchWorkflowInstancesCount).toBeCalledWith(
          filtersMap[key]
        );
        expect(node.state(key)).toBe(INSTANCES_COUNT);
      });
    });
  });

  describe('links highlights', () => {
    it('should highlight filters link when filters is not collapsed', () => {
      // (1) when filters is not collapsed
      // given
      const mockProps = {
        ...mockCollapsablePanelProps,
        active: 'instances',
        isFiltersCollapsed: false,
        getStateLocally: () => ({})
      };
      const node = shallow(<Header {...mockProps} />);
      let filtersNode = node.find('[data-test="header-link-filters"]');

      // then
      expect(filtersNode.find(Styled.ListLink).prop('isActive')).toBe(true);

      // (2) when filters is collapsed
      // given
      node.setProps({isFiltersCollapsed: true});
      node.update();
      filtersNode = node.find('[data-test="header-link-filters"]');

      // then
      expect(filtersNode.find(Styled.ListLink).prop('isActive')).toBe(false);
    });

    it('should highlight selections link when selections is not collapsed', () => {
      // (1) when selections is not collapsed
      // given
      const mockProps = {
        ...mockCollapsablePanelProps,
        isSelectionsCollapsed: false,
        active: 'instances',
        getStateLocally: () => ({})
      };
      const node = shallow(<Header {...mockProps} />);
      let selectionsNode = node.find('[data-test="header-link-selections"]');

      // then
      expect(selectionsNode.find(Styled.ListLink).prop('isActive')).toBe(true);

      // (2) when selections is collapsed
      // given
      node.setProps({isSelectionsCollapsed: true});
      node.update();
      selectionsNode = node.find('[data-test="header-link-selections"]');

      // then
      expect(selectionsNode.find(Styled.ListLink).prop('isActive')).toBe(false);
    });

    it('should highlight running instance link when the filter equals selections', () => {
      // (1) when filter equals runningInstances
      // given
      const mockProps = {
        ...mockCollapsablePanelProps,
        getStateLocally: () => ({}),
        active: 'instances'
      };
      const node = shallow(<Header {...mockProps} />);
      node.setState({filter: FILTER_SELECTION.running});
      node.update();
      let instancesNode = node.find('[data-test="header-link-instances"]');

      // then
      expect(instancesNode.find(Styled.ListLink).prop('isActive')).toBe(true);

      // (1) when filter does not equal runningInstances
      // given
      node.setState({filter: {}});
      node.update();
      instancesNode = node.find('[data-test="header-link-instances"]');

      // then
      expect(instancesNode.find(Styled.ListLink).prop('isActive')).toBe(false);
    });
  });

  it('should highlight incidents link when the filter equals incidents', () => {
    // (1) when filter equals incidents
    // given
    const mockProps = {
      ...mockCollapsablePanelProps,
      getStateLocally: () => ({})
    };
    const node = shallow(<Header {...mockProps} />);
    node.setState({filter: {incidents: true}});
    node.update();
    let incidentsNode = node.find('[data-test="header-link-incidents"]');

    // then
    expect(incidentsNode.find(Styled.ListLink).prop('isActive')).toBe(false);

    // (1) when filter does not equal runningInstances
    // given
    node.setState({filter: {}});
    node.update();
    incidentsNode = node.find('[data-test="header-link-incidents"]');

    // then
    expect(incidentsNode.find(Styled.ListLink).prop('isActive')).toBe(false);
  });

  it('should show the counts and their labels', () => {
    // given
    const mockProps = {
      ...mockCollapsablePanelProps,
      getStateLocally: () => ({}),
      runningInstancesCount: 1,
      filterCount: 2,
      selectionCount: 3,
      instancesInSelectionsCount: 4,
      incidentsCount: 5
    };

    const node = shallow(<Header {...mockProps} />);

    // then
    // instances node
    const instancesNode = node.find('[data-test="header-link-instances"]');
    expect(instancesNode.contains('Running Instances')).toBe(true);
    expect(
      instancesNode.find(Badge).contains(mockProps.runningInstancesCount)
    ).toBe(true);
    expect(instancesNode.find(Styled.ListLink).prop('onClick')).toBe(
      mockCollapsablePanelProps.expandFilters
    );

    // filters node
    const filtersNode = node.find('[data-test="header-link-filters"]');
    expect(filtersNode.contains('Filters')).toBe(true);
    expect(filtersNode.find(Badge).contains(mockProps.filterCount)).toBe(true);
    expect(filtersNode.find(Styled.ListLink).prop('onClick')).toBe(
      mockCollapsablePanelProps.expandFilters
    );

    // selections node
    const selectionsNode = node.find('[data-test="header-link-selections"]');
    expect(selectionsNode.contains('Selections')).toBe(true);
    expect(
      selectionsNode
        .find(Styled.SelectionBadgeLeft)
        .contains(mockProps.selectionCount)
    ).toBe(true);
    expect(
      selectionsNode
        .find(ComboBadge.Right)
        .contains(mockProps.instancesInSelectionsCount)
    ).toBe(true);
    expect(selectionsNode.find(Styled.ListLink).prop('onClick')).toBe(
      mockCollapsablePanelProps.expandSelections
    );

    // incidents node
    const incidentsNode = node.find('[data-test="header-link-incidents"]');
    expect(incidentsNode.contains('Incidents')).toBe(true);
    expect(incidentsNode.find(Badge).contains(mockProps.incidentsCount)).toBe(
      true
    );
    expect(incidentsNode.find(Styled.ListLink).prop('onClick')).toBe(
      mockCollapsablePanelProps.expandFilters
    );
  });

  it('it should request user information', async () => {
    const mockProps = {
      ...mockCollapsablePanelProps,
      getStateLocally: () => ({})
    };

    shallow(<Header {...mockProps} />);

    await flushPromises();
    expect(api.fetchUser).toHaveBeenCalled();
  });

  it('it should display user firstname and lastname', async () => {
    const mockProps = {
      ...mockCollapsablePanelProps,
      getStateLocally: () => ({})
    };
    const node = shallow(<Header {...mockProps} />);

    // await user data fetching
    await flushPromises();

    // check state is updated with user info
    const state = node.state('user');
    expect(state).toHaveProperty(`firstname`, USER.firstname);
    expect(state).toHaveProperty(`lastname`, USER.lastname);

    // check user firstname and lastname are shown in the Header
    const DropdownLabel = node.find('Dropdown').prop('label');
    expect(DropdownLabel).toContain(USER.firstname);
    expect(DropdownLabel).toContain(USER.lastname);
  });

  it('should logout the user when calling handleLogout', async () => {
    api.logout = mockResolvedAsyncFn();
    const mockProps = {
      ...mockCollapsablePanelProps,
      getStateLocally: () => ({})
    };
    const node = shallow(<Header {...mockProps} />);

    await node.instance().handleLogout();

    await flushPromises();

    expect(node.state()).toHaveProperty(`forceRedirect`, true);
  });

  it('assign handleLogout as a Dropdown.Option onClick', () => {
    const mockProps = {
      ...mockCollapsablePanelProps,
      getStateLocally: () => ({})
    };
    const node = shallow(<Header {...mockProps} />);
    const handler = node.instance().handleLogout;
    const onClick = node.find(Dropdown.Option).prop('onClick');

    expect(handler).toEqual(onClick);
  });

  it('should contain links to dashboard and instances page', () => {
    const mockProps = {
      ...mockCollapsablePanelProps,
      getStateLocally: () => ({})
    };
    const node = shallow(<Header {...mockProps} />);

    expect(node).toMatchSnapshot();
  });
});
