import React from 'react';
import { shallow, mount } from 'enzyme';

import LogoutButton from './LogoutButton';
import {getToken, destroy} from 'credentials';
import {get} from 'request';

jest.mock('credentials', () => {return {
  destroy: jest.fn()
}});
jest.mock('request', () => {return {
  get: jest.fn()
}});
jest.mock('react-router-dom', () => { return {
  Link: ({children, onClick}) => {return <a onClick={onClick}>{children}</a>}
}});

it('renders without crashing', () => {
  shallow(<LogoutButton />);
});

it('should clear the token and logout from server on click', () => {
  const node = mount(<LogoutButton />);

  node.find('a').simulate('click');

  expect(destroy).toHaveBeenCalled();
  expect(get).toHaveBeenCalledWith(expect.stringContaining('logout'));
});
