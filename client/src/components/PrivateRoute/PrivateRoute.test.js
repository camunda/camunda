import React from 'react';
import {mount} from 'enzyme';

import PrivateRoute from './PrivateRoute';

import {isLoggedIn} from 'credentials';
import {addHandler, removeHandler} from 'request';

const TestComponent = () => <div>TestComponent</div>;

jest.mock('credentials', () => {
  return {
    isLoggedIn: jest.fn()
  };
});
jest.mock('request', () => {
  return {
    addHandler: jest.fn(),
    removeHandler: jest.fn()
  };
});
jest.mock('react-router-dom', () => {
  return {
    Redirect: ({to}) => {
      return (
        <div>
          REDIRECT to {to.pathname} from {to.state.from}
        </div>
      );
    },
    Route: props => {
      return props.render(props);
    }
  };
});

it('should render the component if the user is logged in', () => {
  isLoggedIn.mockReturnValueOnce(true);

  const node = mount(<PrivateRoute component={TestComponent} />);

  expect(node).toIncludeText('TestComponent');
});

it('should redirect to the login screen if the user is not logged in', () => {
  isLoggedIn.mockReturnValueOnce(false);

  const node = mount(<PrivateRoute component={TestComponent} />);

  expect(node).toIncludeText('REDIRECT to /login');
});

it('should provide the login component with the requested route', () => {
  isLoggedIn.mockReturnValueOnce(false);

  const node = mount(<PrivateRoute component={TestComponent} location="/private" />);

  expect(node).toIncludeText('REDIRECT');
  expect(node).toIncludeText('from /private');
});

it('should register a response handler', () => {
  isLoggedIn.mockReturnValueOnce(true);

  mount(<PrivateRoute component={TestComponent} location="/private" />);

  expect(addHandler).toHaveBeenCalled();
});

it('should unregister the response handler when it is destroyed', () => {
  isLoggedIn.mockReturnValueOnce(true);

  const node = mount(<PrivateRoute component={TestComponent} location="/private" />);
  node.unmount();

  expect(removeHandler).toHaveBeenCalled();
});

it('should redirect to login screen when handler received unauthorized response', () => {
  isLoggedIn.mockReturnValueOnce(true);
  addHandler.mockClear();

  const node = mount(<PrivateRoute component={TestComponent} location="/private" />);
  addHandler.mock.calls[0][0]({status: 401});

  expect(node).toIncludeText('REDIRECT');
  expect(node).toIncludeText('from /private');
});
