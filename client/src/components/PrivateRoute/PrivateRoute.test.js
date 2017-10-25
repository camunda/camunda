import React from 'react';
import {mount} from 'enzyme';

import PrivateRoute from './PrivateRoute';

import {getToken, destroy} from 'credentials';
import {addHandler, removeHandler} from 'request';

const TestComponent = () => <div>TestComponent</div>;

jest.mock('credentials', () => {return {
  getToken: jest.fn(),
  destroy: jest.fn()
}});
jest.mock('request', () => {return {
  addHandler: jest.fn(),
  removeHandler: jest.fn()
}});
jest.mock('react-router-dom', () => {return {
  Redirect: ({to}) => {return <div>REDIRECT to {to.pathname} from {to.state.from}</div>},
  Route: props => {return props.render(props) }
}});

it('should render the component if the user is logged in', () => {
  getToken.mockReturnValueOnce(true);

  const node = mount(<PrivateRoute component={TestComponent} />);

  expect(node).toIncludeText('TestComponent');
});

it('should redirect to the login screen if the user is not logged in', () => {
  getToken.mockReturnValueOnce(false);

  const node = mount(<PrivateRoute component={TestComponent} />);

  expect(node).toIncludeText('REDIRECT to /login');
});

it('should provide the login component with the requested route', () => {
  getToken.mockReturnValueOnce(false);

  const node = mount(<PrivateRoute component={TestComponent} location='/private' />);

  expect(node).toIncludeText('REDIRECT');
  expect(node).toIncludeText('from /private');
});

it('should register a response handler', () => {
  getToken.mockReturnValueOnce(true);

  mount(<PrivateRoute component={TestComponent} location='/private' />);

  expect(addHandler).toHaveBeenCalled();
});

it('should unregister the response handler when it is destroyed', () => {
  getToken.mockReturnValueOnce(true);

  const node = mount(<PrivateRoute component={TestComponent} location='/private' />);
  node.unmount();

  expect(removeHandler).toHaveBeenCalled();
});

it('should redirect to login screen when handler received unauthorized response', () => {
  getToken.mockReturnValueOnce(true);
  addHandler.mockClear();

  const node = mount(<PrivateRoute component={TestComponent} location='/private' />);
  addHandler.mock.calls[0][0]({status: 401});

  expect(node).toIncludeText('REDIRECT');
  expect(node).toIncludeText('from /private');
});

it('should remove login information when handler received unauthorized response', () => {
  getToken.mockReturnValueOnce(true);
  addHandler.mockClear();

  mount(<PrivateRoute component={TestComponent} location='/private' />);
  addHandler.mock.calls[0][0]({status: 401});

  expect(destroy).toHaveBeenCalled();
});
