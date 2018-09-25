import React from 'react';
import {shallow} from 'enzyme';
import Dropdown from 'modules/components/Dropdown';
import * as api from 'modules/api/header/header';
import * as instancesApi from 'modules/api/instances/instances';
import {flushPromises, mockResolvedAsyncFn} from 'modules/testUtils';

import HeaderWrapper from './Header';
import * as Styled from './styled';
import {localStateKeys, apiKeys, filtersMap} from './constants';

const {WrappedComponent: Header} = HeaderWrapper;

const USER = {
  user: {
    firstname: 'Jonny',
    lastname: 'Prosciutto'
  }
};

const INSTANCES_COUNT = 23;

api.fetchUser = mockResolvedAsyncFn(USER);
instancesApi.fetchWorkflowInstancesCount = mockResolvedAsyncFn(INSTANCES_COUNT);

describe('Header', () => {
  beforeEach(() => {
    api.fetchUser.mockClear();
    instancesApi.fetchWorkflowInstancesCount.mockClear();
  });

  describe('localState counts', () => {
    it("should get the count from props if it's provided", () => {
      localStateKeys.forEach(key => {
        // given
        const count = 23;
        const mockProps = {[key]: count, getStateLocally: () => ({})};
        const node = shallow(<Header {...mockProps} />);

        // then
        expect(node.state(key)).toBe(count);
      });
    });

    it("should get the count from localState if it's not in the props", () => {
      localStateKeys.forEach(key => {
        // given
        const count = 23;
        const mockLocalState = {[key]: count};
        const mockProps = {[key]: count, getStateLocally: () => mockLocalState};
        const node = shallow(<Header {...mockProps} />);

        // then
        expect(node.state(key)).toBe(count);
      });
    });

    it('should set tbe count to 0 if not present in props or localState', () => {
      localStateKeys.forEach(key => {
        // given
        const mockProps = {getStateLocally: () => ({})};
        const node = shallow(<Header {...mockProps} />);

        // then
        expect(node.state(key)).toBe(0);
      });
    });
  });

  describe('api counts', () => {
    it("should get the count from props if it's provided", () => {
      apiKeys.forEach(key => {
        // given
        const count = 23;
        const mockProps = {[key]: count, getStateLocally: () => ({})};
        const node = shallow(<Header {...mockProps} />);

        // then
        expect(node.state(key)).toBe(count);
      });
    });

    it("should get the count from api if it's not in the props", async () => {
      // given
      const mockProps = {getStateLocally: () => ({})};
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

  it('should show the counts and their labels', () => {
    // given
    const mockProps = {
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
    const instancesNode = node.find('[data-test="instances"]');
    expect(instancesNode.contains('Instances')).toBe(true);
    expect(instancesNode.find(Styled.Badge).prop('badgeContent')).toBe(
      mockProps.runningInstancesCount
    );

    // filters node
    const filtersNode = node.find('[data-test="filters"]');
    expect(filtersNode.contains('Filters')).toBe(true);
    expect(filtersNode.find(Styled.Badge).prop('badgeContent')).toBe(
      mockProps.filterCount
    );

    // selections node
    const selectionsNode = node.find('[data-test="selections"]');
    expect(selectionsNode.contains('Selections')).toBe(true);
    expect(
      selectionsNode.find(Styled.SelectionBadge).prop('badgeContent')
    ).toBe(mockProps.instancesInSelectionsCount);
    expect(
      selectionsNode.find(Styled.SelectionBadge).prop('circleContent')
    ).toBe(mockProps.selectionCount);

    // incidents node
    const incidentsNode = node.find('[data-test="incidents"]');
    expect(incidentsNode.contains('Incidents')).toBe(true);
    expect(incidentsNode.find(Styled.Badge).prop('badgeContent')).toBe(
      mockProps.incidentsCount
    );
  });

  it('it should request user information', async () => {
    const mockProps = {
      getStateLocally: () => ({})
    };

    shallow(<Header {...mockProps} />);

    await flushPromises();
    expect(api.fetchUser).toHaveBeenCalled();
  });

  it('it should display user firstname and lastname', async () => {
    const mockProps = {
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
      getStateLocally: () => ({})
    };
    const node = shallow(<Header {...mockProps} />);

    await node.instance().handleLogout();

    await flushPromises();

    expect(node.state()).toHaveProperty(`forceRedirect`, true);
  });

  it('assign handleLogout as a Dropdown.Option onClick', () => {
    const mockProps = {
      getStateLocally: () => ({})
    };
    const node = shallow(<Header {...mockProps} />);
    const handler = node.instance().handleLogout;
    const onClick = node.find(Dropdown.Option).prop('onClick');

    expect(handler).toEqual(onClick);
  });

  it('should contain links to dashboard and instances page', () => {
    const mockProps = {
      getStateLocally: () => ({})
    };
    const node = shallow(<Header {...mockProps} />);

    expect(node).toMatchSnapshot();
  });
});
