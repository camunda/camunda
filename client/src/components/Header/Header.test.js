import React from 'react';
import { shallow, mount } from 'enzyme';

import Header from './Header';
import {getToken} from 'credentials';

jest.mock('credentials', () => {return {
  getToken: jest.fn(),
}});
jest.mock('react-router-dom', () => { return {
  Link: ({children}) => {return <a>{children}</a>}
}});
jest.mock('./LogoutButton', () => {return () => <div>logout</div>});
jest.mock('./HeaderNav', () => {
  const HeaderNav = () => <div>HeaderNav</div>;
  HeaderNav.Item = () => <div>HeaderNavItem</div>;
  return HeaderNav;
});
jest.mock('components', () => {return {
  Logo: () => <div></div>
  }
});


it('renders without crashing', () => {
  getToken.mockReturnValue(true);

  shallow(<Header />);
});

it('includes the name provided as property', () => {
  const name = 'Awesome App';
  getToken.mockReturnValue(true);


  const node = mount(<Header name={name} />);
  expect(node).toIncludeText(name);
});

it('does not render the header if the user is not logged in', () => {
  getToken.mockReturnValue(false);

  const node = mount(<Header />);

  expect(node.getDOMNode()).toBe(null);
});

it('does render the navigation and the logout button if the user is logged in', () => {
  getToken.mockReturnValue(true);

  const node = mount(<Header />);

  expect(node).toIncludeText('HeaderNav');
  expect(node).toIncludeText('logout');
});
