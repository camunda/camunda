/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {runAllEffects, runAllCleanups} from 'react';
import {shallow} from 'enzyme';

import {addHandler, removeHandler} from 'request';

import {Header, Footer} from '..';

import {PrivateRoute} from './PrivateRoute';

const TestComponent = () => <div>TestComponent</div>;
let originalWindowLocation = window.location;

jest.mock('request', () => {
  return {
    addHandler: jest.fn(),
    removeHandler: jest.fn(),
  };
});

beforeEach(() => {
  Object.defineProperty(window, 'location', {
    configurable: true,
    enumerable: true,
    value: new URL(window.location.href),
  });

  jest.clearAllMocks();
});

afterEach(() => {
  Object.defineProperty(window, 'location', {
    configurable: true,
    enumerable: true,
    value: originalWindowLocation,
  });
});

it('should render the component if the user is logged in', () => {
  const node = shallow(<PrivateRoute component={TestComponent} />).renderProp('render')({});

  expect(node).toIncludeText('TestComponent');
});

it('should use render method to render a component when specified', () => {
  const node = shallow(<PrivateRoute render={() => <h1>someText</h1>} />).renderProp('render')({});

  expect(node).toIncludeText('someText');
});

it('should reload the page on 401 request to reinitialize the login flow', () => {
  window.location.reload = jest.fn();
  shallow(<PrivateRoute component={TestComponent} />);

  runAllEffects();

  const handler = addHandler.mock.calls[0][0];
  handler({status: 401});

  expect(window.location.reload).toHaveBeenCalled();
});

it('should register a response handler', () => {
  shallow(<PrivateRoute component={TestComponent} />);

  runAllEffects();

  expect(addHandler).toHaveBeenCalled();
});

it('should unregister the response handler when it is destroyed', async () => {
  shallow(<PrivateRoute component={TestComponent} />);

  runAllCleanups();

  expect(removeHandler).toHaveBeenCalled();
});

it('should include a header and footer page', () => {
  const node = shallow(<PrivateRoute component={TestComponent} />);

  const content = node.find('Route').renderProp('render')();

  expect(content.find(Header)).toExist();
  expect(content.find(Footer)).toExist();
});
