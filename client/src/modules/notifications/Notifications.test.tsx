/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {shallow} from 'enzyme';

import {ErrorResponse} from 'request';

import {default as Notifications, addNotification, showError} from './Notifications';
import {Config} from './Notification';

it('should expose a global addNotifications method', () => {
  const node = shallow(<Notifications />);

  addNotification('Sample Notification');

  expect(node.find('Notification')).toExist();
  expect(node.find('Notification').prop<Config>('config').text).toBe('Sample Notification');
});

it('should process and show an error notification', async () => {
  const node = shallow(<Notifications />);

  await showError({message: 'Error content'} as ErrorResponse);

  expect(node.find('Notification')).toExist();
  expect(node.find('Notification').prop<Config>('config').text).toBe('Error content');
});

it('should accept string error', async () => {
  const node = shallow(<Notifications />);

  await showError('Error content');

  expect(node.find('Notification').prop<Config>('config').text).toBe('Error content');
});

it('should accept React element error', async () => {
  const node = shallow(<Notifications />);

  await showError(<h1>test</h1>);

  const test = shallow(node.find('Notification').prop<Config>('config').text as React.ReactElement);

  expect(test.find('h1')).toExist();
});
