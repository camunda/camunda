/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {default as Notifications, addNotification, showError} from './Notifications';

it('should render Notifications', () => {
  const node = shallow(<Notifications />);

  node.setState({
    notifications: [
      {id: 'a', text: 'Notification 1'},
      {id: 'b', text: 'Notification 2'},
    ],
  });

  expect(node).toMatchSnapshot();
});

it('should expose a global addNotifications method', () => {
  const node = shallow(<Notifications />);

  addNotification('Sample Notification');

  expect(node.find('Notification')).toExist();
  expect(node.find('Notification').prop('config').text).toBe('Sample Notification');
});

it('should process and show an error notification', async () => {
  const node = shallow(<Notifications />);

  await showError({message: 'Error content'});

  expect(node.find('Notification')).toExist();
  expect(node.find('Notification').prop('config').text).toBe('Error content');
});

it('should accept string error', async () => {
  const node = shallow(<Notifications />);

  await showError('Error content');

  expect(node.find('Notification').prop('config').text).toBe('Error content');
});
