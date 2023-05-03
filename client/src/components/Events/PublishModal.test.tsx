/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {runLastEffect} from '__mocks__/react';
import {shallow} from 'enzyme';

import {showError, addNotification} from 'notifications';
import {User} from 'HOC';

import {publish, getUsers} from './service';
import {PublishModal} from './PublishModal';

jest.mock('./service', () => ({
  publish: jest.fn(),
  getUsers: jest.fn().mockReturnValue([
    {
      id: 'USER:demo',
      identity: {
        id: 'demo',
      },
    },
  ]),
}));

jest.mock('notifications', () => ({
  showError: jest.fn(),
  addNotification: jest.fn(),
}));

const props = {
  user: {id: 'demo'} as User,
  getUser: jest.fn(),
  refreshUser: jest.fn(),
  id: 'processId',
  onPublish: jest.fn(),
  onClose: jest.fn(),
  republish: false,
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
};

it('should show different text depending on the republish prop', () => {
  const initialPublish = shallow(<PublishModal {...props} />);

  runLastEffect();

  expect(initialPublish).toMatchSnapshot();

  const republish = shallow(<PublishModal {...props} republish />);

  runLastEffect();

  expect(republish).toMatchSnapshot();
});

it('should allow publishing the process with the given id', () => {
  const node = shallow(<PublishModal {...props} />);
  node.find('.confirm').simulate('click');

  expect(publish).toHaveBeenCalledWith('processId');
});

it('should show a success notification', () => {
  (addNotification as jest.Mock).mockClear();
  const node = shallow(<PublishModal {...props} />);
  node.find('.confirm').simulate('click');

  expect(addNotification).toHaveBeenCalled();
  expect((addNotification as jest.Mock).mock.calls[0]).toMatchSnapshot();
});

it('should show an error message', () => {
  (showError as jest.Mock).mockClear();
  const node = shallow(
    <PublishModal
      {...props}
      mightFail={jest.fn().mockImplementation((data, cb, err) => err('errorMessage'))}
    />
  );
  node.find('.confirm').simulate('click');

  expect(showError).toHaveBeenCalled();
  expect((showError as jest.Mock).mock.calls[0]).toMatchSnapshot();
});

it('should show that access is granted if there are more than one user', () => {
  (getUsers as jest.Mock).mockReturnValue([{}, {}]);
  const node = shallow(<PublishModal {...props} />);

  runLastEffect();

  expect(node.find('.permission')).toMatchSnapshot();
});
