/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {runLastEffect} from 'react';
import {shallow} from 'enzyme';

import {isEmailEnabled} from 'config';
import {Input, Select, UserTypeahead} from 'components';
import {numberParser} from 'services';

import {ConfigureProcessModal} from './ConfigureProcessModal';

jest.mock('config', () => ({
  isEmailEnabled: jest.fn().mockReturnValue(true),
  getOptimizeProfile: jest.fn().mockReturnValue('platform'),
}));

jest.mock('services', () => ({
  ...jest.requireActual('services'),
  numberParser: {isPositiveInt: jest.fn().mockReturnValue(true)},
}));

const digest = {enabled: false, checkInterval: {value: 1, unit: 'week'}};
const props = {
  initialConfig: {
    owner: {id: null, name: null},
    digest,
  },
};

it('should load the initial user into the user dropdown', () => {
  const node = shallow(
    <ConfigureProcessModal initialConfig={{owner: {id: 'test', name: 'testName'}, digest}} />
  );

  expect(node.find(UserTypeahead).prop('users')).toEqual([
    {id: 'USER:test', identity: {id: 'test', name: 'testName'}},
  ]);
});

it('should invoke the onConfirm with the updated config', async () => {
  const spy = jest.fn();
  const node = shallow(<ConfigureProcessModal onConfirm={spy} {...props} />);

  await runLastEffect();
  await node.update();

  node
    .find(UserTypeahead)
    .simulate('change', [{id: 'USER:test', identity: {id: 'test', name: 'testName'}}]);

  node.find('Switch').simulate('change', {target: {checked: true}});
  node.find(Input).simulate('change', {target: {value: 2}});
  node.find(Select).simulate('change', 'months');

  node.find('.confirm').simulate('click');

  expect(spy).toHaveBeenCalledWith(
    {
      ownerId: 'test',
      processDigest: {
        enabled: true,
        checkInterval: {
          value: 2,
          unit: 'months',
        },
      },
    },
    true,
    'testName'
  );
});

it('should disable the digest when removing the owner', () => {
  const spy = jest.fn();
  const node = shallow(
    <ConfigureProcessModal
      onConfirm={spy}
      initialConfig={{owner: {id: 'test', name: 'testName'}, digest}}
    />
  );

  node.find('Switch').simulate('change', {target: {checked: true}});
  expect(node.find('Switch').prop('checked')).toBe(true);

  node.find(UserTypeahead).simulate('change', []);
  expect(node.find('Switch').prop('checked')).toBe(false);
});

it('should disable the confirm button if no changes to the modal were applied', () => {
  const spy = jest.fn();
  const node = shallow(
    <ConfigureProcessModal
      onConfirm={spy}
      initialConfig={{owner: {id: 'test', name: 'testName'}, digest}}
    />
  );

  expect(node.find('.confirm').prop('disabled')).toBe(true);
});

it('should show warning that email is not configured', async () => {
  isEmailEnabled.mockReturnValueOnce(false);
  const node = shallow(<ConfigureProcessModal {...props} />);

  expect(node.find('MessageBox').exists()).toBe(true);
});

it('should disable the confirm button if invalid interval value is provided', async () => {
  numberParser.isPositiveInt.mockReturnValue(false);
  const node = shallow(
    <ConfigureProcessModal initialConfig={{owner: {id: 'test', name: 'testName'}, digest}} />
  );

  node.find('Switch').simulate('change', {target: {checked: true}});

  expect(node.find('.confirm').prop('disabled')).toBe(true);
});
