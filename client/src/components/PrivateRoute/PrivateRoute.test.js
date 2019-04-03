/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

import PrivateRoute from './PrivateRoute';

import {addHandler, removeHandler} from 'request';

const TestComponent = () => <div>TestComponent</div>;

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
  const node = mount(<PrivateRoute component={TestComponent} />);

  expect(node).toIncludeText('TestComponent');
});

it('should provide the login component with the requested route', () => {
  const node = mount(<PrivateRoute component={TestComponent} location="/private" />);

  node.setState({forceRedirect: true}, () => {
    expect(node).toIncludeText('REDIRECT');
    expect(node).toIncludeText('from /private');
  });
});

it('should register a response handler', () => {
  mount(<PrivateRoute component={TestComponent} location="/private" />);

  expect(addHandler).toHaveBeenCalled();
});

it('should unregister the response handler when it is destroyed', () => {
  const node = mount(<PrivateRoute component={TestComponent} location="/private" />);
  node.unmount();

  expect(removeHandler).toHaveBeenCalled();
});
