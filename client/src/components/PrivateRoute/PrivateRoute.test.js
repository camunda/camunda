/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {runAllEffects, runAllCleanups} from 'react';
import {shallow} from 'enzyme';

import {addHandler, removeHandler} from 'request';
import {showError} from 'notifications';

import {Header, Footer} from '..';

import {PrivateRoute} from './PrivateRoute';
import {Login} from './Login';

const TestComponent = () => <div>TestComponent</div>;

jest.mock('request', () => {
  return {
    addHandler: jest.fn(),
    removeHandler: jest.fn(),
  };
});

jest.mock('notifications', () => ({
  showError: jest.fn(),
}));

beforeEach(() => {
  jest.clearAllMocks();
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

  runAllEffects();

  const handler = addHandler.mock.calls[0][0];
  handler({status: 401}, {url: 'api/entities'});

  const wrapper = node.renderProp('render')({});
  expect(wrapper.find(Login)).toExist();
});

it('should register a response handler', () => {
  shallow(<PrivateRoute component={TestComponent} />);

  runAllEffects();

  expect(addHandler).toHaveBeenCalled();
});

describe('session timeout', () => {
  shallow(<PrivateRoute component={TestComponent} />);

  runAllEffects();

  const handler = addHandler.mock.calls[1][0];

  it('should not show an error message if the user comes to the page initially', () => {
    handler({status: 401}, {url: 'api/entities'});

    expect(showError).not.toHaveBeenCalled();
  });

  it('should show an error message if the session times out', async () => {
    await handler({status: 200}, {url: 'api/entities'});
    await handler({status: 401}, {url: 'api/entities'});

    expect(showError).toHaveBeenCalled();
  });

  it('should not show an error message if the user logs out manually', () => {
    handler({status: 200}, {url: 'api/entities'});
    handler({status: 200}, {url: 'api/authentication/logout'});
    handler({status: 401}, {url: 'api/entities'});

    expect(showError).not.toHaveBeenCalled();
  });
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

it('should not include a header when showing the login screen', () => {
  const node = shallow(<PrivateRoute component={TestComponent} />);
  runAllEffects();

  const handler = addHandler.mock.calls[0][0];
  handler({status: 401}, {url: 'api/entities'});

  const content = node.find('Route').renderProp('render')();

  expect(content.find(Header)).not.toExist();
  expect(content.find(Footer)).not.toExist();
});
