/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import AppWithErrorHandling from './App';
import {addNotification} from 'notifications';

jest.mock('notifications', () => ({addNotification: jest.fn(), Notifications: () => <span />}));

const App = AppWithErrorHandling.WrappedComponent;

jest.mock('translation', () => ({
  init: jest.fn()
}));

const props = {
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data))
};

it('should include a header for the Alert page', async () => {
  const node = shallow(<App {...props} />);
  await node.update();
  const content = shallow(node.find('Route').prop('render')({location: {pathname: '/'}}));

  expect(content.find('withRouter(Header)')).toExist();
  expect(content.find('Footer')).toExist();
});

it('should not include a header for shared resources', async () => {
  const node = shallow(<App {...props} />);
  await node.update();
  const content = shallow(
    node.find('Route').prop('render')({location: {pathname: '/share/report/3'}})
  );

  expect(content.find('Header')).not.toExist();
  expect(content.find('Footer')).not.toExist();
});

it('should show a nofitication error when not able to initilize the translation', async () => {
  const node = shallow(<App mightFail={(promise, cb, fail) => fail()} />);
  await node.update();
  expect(node.state().error).toBe(true);
  expect(addNotification).toHaveBeenCalled();
});
