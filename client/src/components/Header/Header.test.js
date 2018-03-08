import React from 'react';
import {shallow, mount} from 'enzyme';

import Header from './Header';

jest.mock('react-router-dom', () => {
  return {
    Link: ({children}) => {
      return <a>{children}</a>;
    }
  };
});
jest.mock('./LogoutButton', () => {
  return () => <div>logout</div>;
});
jest.mock('./HeaderNav', () => {
  const HeaderNav = () => <div>HeaderNav</div>;
  HeaderNav.Item = () => <div>HeaderNavItem</div>;
  return HeaderNav;
});
jest.mock('components', () => {
  return {
    Logo: () => <div />
  };
});

it('renders without crashing', () => {
  shallow(<Header />);
});

it('includes the name provided as property', () => {
  const name = 'Awesome App';
  const node = mount(<Header name={name} />);
  expect(node).toIncludeText(name);
});

it('does render the navigation and the logout button if the user is logged in', () => {
  const node = mount(<Header />);

  expect(node).toIncludeText('HeaderNav');
  expect(node).toIncludeText('logout');
});
