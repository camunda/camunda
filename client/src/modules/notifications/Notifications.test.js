/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {default as Notifications, addNotification} from './Notifications';

it('should render Notifications', () => {
  const node = shallow(<Notifications />);

  node.setState({
    notifications: [{id: 'a', text: 'Notification 1'}, {id: 'b', text: 'Notification 2'}]
  });

  expect(node).toMatchSnapshot();
});

it('should expose a global addNotifications method', () => {
  const node = shallow(<Notifications />);

  addNotification('Sample Notification');

  expect(node.find('Notification')).toBePresent();
  expect(node.find('Notification').prop('config').text).toBe('Sample Notification');
});
