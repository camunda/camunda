import React from 'react';
import {shallow} from 'enzyme';
import Header from './Header';
import Dropdown from 'modules/components/Dropdown';
import * as api from './api';
import {flushPromises, mockResolvedAsyncFn} from 'modules/testUtils';

const USER = {
  user: {
    firstname: 'Jonny',
    lastname: 'Prosciutto'
  }
};

api.user = mockResolvedAsyncFn(USER);

describe('Header', () => {
  it('should show the count of all instances', () => {
    const node = shallow(<Header active="dashboard" instances={123} />);

    const badge = node.find('Styled(Badge)[type="instances"]').render();

    expect(badge.text()).toEqual('123');
  });

  it('should show other provided properties', () => {
    const node = shallow(
      <Header
        active="dashboard"
        instances={123}
        filters={1}
        selections={1}
        incidents={1}
      />
    );

    expect(node.find('Styled(Badge)[type="filters"]')).toExist();
    expect(node.find('Styled(Badge)[type="selections"]')).toExist();
    expect(node.find('Styled(Badge)[type="incidents"]')).toExist();
  });

  it('should show the 0 if badge label is not provided', () => {
    const node = shallow(
      <Header active="dashboard" instances={123} incidents={1} />
    );

    expect(
      node
        .find('Styled(Badge)[type="filters"]')
        .render()
        .text()
    ).toBe('0');
    expect(
      node
        .find('Styled(Badge)[type="selections"]')
        .render()
        .text()
    ).toBe('0');
    expect(node.find('Styled(Badge)[type="incidents"]')).toExist();
  });

  it('it should show the instances field even if there are no instances', () => {
    const node = shallow(<Header active="dashboard" instances={0} />);

    expect(node.find('Styled(Badge)[type="instances"]')).toExist();
  });

  it('it should request user information', async () => {
    const node = shallow(<Header active="dashboard" instances={0} />);
    const spyFetch = jest.spyOn(node.instance(), 'fetchUser');

    await node.instance().componentDidMount();
    expect(spyFetch).toHaveBeenCalled();
  });

  it('it should display user firstname and lastname', async () => {
    const node = shallow(<Header active="dashboard" instances={0} />);

    // await user data fetching
    await node.instance().componentDidMount();

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
    const node = shallow(<Header active="dashboard" instances={0} />);

    await node.instance().handleLogout();

    await flushPromises();

    expect(node.state()).toHaveProperty(`forceRedirect`, true);
  });

  it('assign handleLogout as a Dropdown.Option onClick', () => {
    const node = shallow(<Header active="dashboard" instances={0} />);
    const handler = node.instance().handleLogout;
    const onClick = node.find(Dropdown.Option).prop('onClick');

    expect(handler).toEqual(onClick);
  });

  it('should contain links to dashboard and instances page', () => {
    const node = shallow(<Header active="dashboard" />);

    expect(node).toMatchSnapshot();
  });
});
