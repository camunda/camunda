import React from 'react';
import {mount} from 'enzyme';

import PrivateRoute from './PrivateRoute';

import {getToken} from 'credentials';

const TestComponent = () => <div>TestComponent</div>;

jest.mock('credentials', () => {return {
  getToken: jest.fn()
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