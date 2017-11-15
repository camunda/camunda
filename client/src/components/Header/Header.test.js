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
jest.mock('./Section', () => {return () => <div>Section</div>});

it('renders without crashing', () => {
  shallow(<Header />);
});

it('includes the name provided as property', () => {
  const name = 'Awesome App';

  const node = mount(<Header name={name} />);
  expect(node).toIncludeText(name);
});

it('does not render the sections or logout button if the user is not logged in', () => {
  getToken.mockReturnValue(false);

  const node = mount(<Header />);

  expect(node).not.toIncludeText('Section');
  expect(node).not.toIncludeText('logout');
});

it('does render the navigation, its sections and the logout button if the user is logged in', () => {
  getToken.mockReturnValue(true);

  const node = mount(<Header />);

  expect(node.find('.Header__nav')).toBePresent();
  expect(node).toIncludeText('Section');
  expect(node).toIncludeText('logout');
});
