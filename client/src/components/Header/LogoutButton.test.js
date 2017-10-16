import React from 'react';
import { shallow, mount } from 'enzyme';

import LogoutButton from './LogoutButton';
import {getToken, destroy} from 'credentials';
import {get} from 'request';

jest.mock('credentials', () => {return {
  getToken: jest.fn(),
  destroy: jest.fn()
}});
jest.mock('request', () => {return {
  get: jest.fn()
}});

it('renders without crashing', () => {
  shallow(<LogoutButton />);
});

it('is displayed when there is an authentication token', () => {
  getToken.mockReturnValueOnce('a-valid-token');

  const node = shallow(<LogoutButton />);

  expect(node).not.toHaveClassName('hidden');
});

it('is hidden when there is no authentication token', () => {
  getToken.mockReturnValueOnce(null);

  const node = shallow(<LogoutButton />);

  expect(node).toHaveClassName('hidden');
});

it('should clear the token and logout from server on click', () => {
  const node = shallow(<LogoutButton />);

  node.find('a').simulate('click');

  expect(destroy).toHaveBeenCalled();
  expect(get).toHaveBeenCalledWith(expect.stringContaining('logout'));
});
