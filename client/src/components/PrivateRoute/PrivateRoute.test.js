/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

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
  const node = shallow(<PrivateRoute component={TestComponent} />).dive();

  expect(node).toIncludeText('TestComponent');
});

it('should provide the login component with the requested route', () => {
  const node = shallow(<PrivateRoute component={TestComponent} location="/private" />);

  node.instance().componentDidUpdate = jest.fn();

  node.setState({forceRedirect: true}, () => {
    const wrapper = node.renderProp('render')({location: 'test'});
    expect(wrapper.find('Redirect')).toBePresent();
    expect(wrapper.props().to.state).toEqual({from: 'test'});
  });
});

it('should register a response handler', () => {
  shallow(<PrivateRoute component={TestComponent} location="/private" />);

  expect(addHandler).toHaveBeenCalled();
});

it('should unregister the response handler when it is destroyed', () => {
  const node = shallow(<PrivateRoute component={TestComponent} location="/private" />);
  node.unmount();

  expect(removeHandler).toHaveBeenCalled();
});
