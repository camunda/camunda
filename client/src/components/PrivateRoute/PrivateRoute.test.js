/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {Header, Footer} from '..';

import PrivateRoute from './PrivateRoute';
import {Login} from './Login';

import {addHandler, removeHandler} from 'request';

const flushPromises = () => new Promise(resolve => setImmediate(resolve));
const TestComponent = () => <div>TestComponent</div>;

jest.mock('request', () => {
  return {
    addHandler: jest.fn(),
    removeHandler: jest.fn()
  };
});

it('should render the component if the user is logged in', () => {
  const node = shallow(<PrivateRoute component={TestComponent} />).renderProp('render')({});

  expect(node.find('Detachable')).toExist();
  const childContent = shallow(node.find('Detachable').prop('children'));

  expect(childContent).toIncludeText('TestComponent');
});

it('should use render method to render a component when specified', () => {
  const node = shallow(<PrivateRoute render={() => <h1>someText</h1>} />).renderProp('render')({});

  expect(node.find('Detachable')).toExist();
  const childContent = shallow(node.find('Detachable').prop('children'));

  expect(childContent).toIncludeText('someText');
});

it('should render the login component', () => {
  const node = shallow(<PrivateRoute component={TestComponent} />);

  node.setState({showLogin: true}, () => {
    const wrapper = node.renderProp('render')({});
    expect(wrapper.find(Login)).toExist();
  });
});

it('should register a response handler', () => {
  shallow(<PrivateRoute component={TestComponent} />);

  expect(addHandler).toHaveBeenCalled();
});

it('should unregister the response handler when it is destroyed', async () => {
  const node = shallow(<PrivateRoute component={TestComponent} />);

  await flushPromises();
  node.unmount();

  expect(removeHandler).toHaveBeenCalled();
});

it('should include a header and footer page', () => {
  const node = shallow(<PrivateRoute component={TestComponent} />);

  const content = node.find('Route').renderProp('render')();

  expect(content.find(Header)).toExist();
  expect(content.find(Footer)).toExist();
});

it('should not include a header when showing the login screen', () => {
  const node = shallow(<PrivateRoute component={TestComponent} />);
  node.setState({showLogin: true});

  const content = node.find('Route').renderProp('render')();

  expect(content.find(Header)).not.toExist();
  expect(content.find(Footer)).not.toExist();
});
