/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import AppWithErrorHandling from './App';

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

it('should show an error message when it is not possible to initilize the translation', async () => {
  const node = shallow(<App {...props} error="test error message" />);

  expect(node).toMatchSnapshot();
});

it('should render the last component in the url', async () => {
  const node = shallow(<App {...props} />);
  await node.update();
  const routes = shallow(
    node.find('Route').prop('render')({
      location: {pathname: '/collection/cid/dashboard/did/report/rid'}
    })
  );

  const renderedEntity = shallow(
    routes.find({path: '/(report|dashboard|collection)/*'}).prop('render')({
      location: {pathname: '/collection/cid/dashboard/did/report/rid'}
    })
  );

  expect(renderedEntity.name()).toBe('Report');
  expect(renderedEntity.props().match.params.id).toBe('rid');
});
