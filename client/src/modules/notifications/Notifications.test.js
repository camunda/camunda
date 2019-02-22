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
