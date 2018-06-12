import React from 'react';
import {mount, shallow} from 'enzyme';
import Header from './Header';
import {Dropdown} from 'modules/components';
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
    const node = mount(<Header active="dashboard" instances={123} />);

    expect(node).toIncludeText('123');
  });

  it('should show other provided properties', () => {
    const node = mount(
      <Header
        active="dashboard"
        instances={123}
        filters={1}
        selections={1}
        incidents={1}
      />
    );

    expect(node).toIncludeText('Filters');
    expect(node).toIncludeText('Selections');
    expect(node).toIncludeText('Incidents');
  });

  it('should not show the labels if they are not provided', () => {
    const node = mount(
      <Header active="dashboard" instances={123} incidents={1} />
    );

    expect(node).not.toIncludeText('Filters');
    expect(node).not.toIncludeText('Selections');
    expect(node).toIncludeText('Incidents');
  });

  it('it should show the instances field even if there are no instances', () => {
    const node = mount(<Header active="dashboard" instances={0} />);

    expect(node).toIncludeText('Instances');
  });

  it('it should request user information', async () => {
    const node = mount(<Header active="dashboard" instances={0} />);
    const spyFetch = jest.spyOn(node.instance(), 'fetchUser');

    await node.instance().componentDidMount();
    expect(spyFetch).toHaveBeenCalled();
  });

  it('it should display user firstname and lastname', async () => {
    const node = mount(<Header active="dashboard" instances={0} />);

    // await user data fetching
    await node.instance().componentDidMount();

    // check state is updated with user info
    const state = node.state('user');
    expect(state).toHaveProperty(`firstname`, USER.firstname);
    expect(state).toHaveProperty(`lastname`, USER.lastname);

    // check user firstname and lastname are shown in the Header
    expect(node.text()).toContain(USER.firstname);
    expect(node.text()).toContain(USER.lastname);
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
});
