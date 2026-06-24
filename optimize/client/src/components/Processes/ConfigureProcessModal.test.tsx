/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {runLastEffect} from '__mocks__/react';
import {shallow} from 'enzyme';

import {useUiConfig} from 'hooks';
import {UserTypeahead} from 'components';

import ConfigureProcessModal from './ConfigureProcessModal';

jest.mock('services', () => ({
  ...jest.requireActual('services'),
  numberParser: {isPositiveInt: jest.fn().mockReturnValue(true)},
}));

jest.mock('hooks', () => ({
  useDocs: jest.fn(() => ({
    generateDocsLink: (url: string) => url,
  })),
  useUiConfig: jest.fn(() => ({
    optimizeProfile: 'ccsm',
    emailEnabled: true,
  })),
}));

const digest = {enabled: false};
const props = {
  onClose: jest.fn(),
  onConfirm: jest.fn(),
  initialConfig: {
    owner: {id: null, name: null},
    digest,
  },
};

it('should load the initial user into the user dropdown', () => {
  const node = shallow(
    <ConfigureProcessModal
      {...props}
      initialConfig={{owner: {id: 'test', name: 'testName'}, digest}}
    />
  );

  expect(node.find(UserTypeahead).prop('users')).toEqual([
    {id: 'USER:test', identity: {id: 'test', name: 'testName', type: 'user'}},
  ]);
});

it('should invoke the onConfirm with the updated config', async () => {
  const spy = jest.fn();
  const node = shallow(<ConfigureProcessModal {...props} onConfirm={spy} />);

  await runLastEffect();
  await node.update();

  node
    .find(UserTypeahead)
    .simulate('change', [{id: 'USER:test', identity: {id: 'test', name: 'testName'}}]);

  node.find('Toggle').simulate('toggle', true);
  node.find('.confirm').simulate('click');

  expect(spy).toHaveBeenCalledWith(
    {
      ownerId: 'test',
      processDigest: {
        enabled: true,
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
      {...props}
      onConfirm={spy}
      initialConfig={{owner: {id: 'test', name: 'testName'}, digest}}
    />
  );

  node.find('Toggle').simulate('toggle', true);
  expect(node.find('Toggle').prop('toggled')).toBe(true);

  node.find(UserTypeahead).simulate('change', []);
  expect(node.find('Toggle').prop('toggled')).toBe(false);
});

it('should disable the confirm button if no changes to the modal were applied', () => {
  const spy = jest.fn();
  const node = shallow(
    <ConfigureProcessModal
      {...props}
      onConfirm={spy}
      initialConfig={{owner: {id: 'test', name: 'testName'}, digest}}
    />
  );

  expect(node.find('.confirm').prop('disabled')).toBe(true);
});

it('should show warning that email is not configured', async () => {
  (useUiConfig as jest.Mock).mockReturnValueOnce({emailEnabled: false, optimizeProfile: 'ccsm'});
  const node = shallow(<ConfigureProcessModal {...props} />);

  expect(node.find('ActionableNotification').exists()).toBe(true);
});
