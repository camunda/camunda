/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {runLastEffect} from '__mocks__/react';
import {shallow} from 'enzyme';
import {MenuItem} from '@carbon/react';

import {getOptimizeProfile} from 'config';

import CreateNewButton from './CreateNewButton';

jest.mock('config', () => ({
  getOptimizeProfile: jest.fn().mockReturnValue('platform'),
}));

const props = {
  createCollection: jest.fn(),
  createProcessReport: jest.fn(),
  createDashboard: jest.fn(),
  importEntity: jest.fn(),
};

it('should not show the collection option if it is in a collection', async () => {
  const node = shallow(<CreateNewButton {...props} />);

  await runLastEffect();

  expect(node.find({label: 'Collection'})).toExist();
  node.setProps({collection: '123'});
  expect(node.find({label: 'Collection'})).not.toExist();
});

it('should not show decision and combined report options in cloud environment', async () => {
  (getOptimizeProfile as jest.Mock).mockReturnValueOnce('cloud');

  const node = shallow(<CreateNewButton {...props} />);

  await runLastEffect();

  expect(node.find({link: 'report/new-decision/edit'})).not.toExist();
  expect(node.find({link: 'report/new-combined/edit'})).not.toExist();
});

it('should call the createCollection prop', () => {
  const spy = jest.fn();
  const node = shallow(<CreateNewButton {...props} createCollection={spy} />);

  node.find(MenuItem).at(0).simulate('click');

  expect(spy).toHaveBeenCalled();
});

it('should call the createProcessReport prop', async () => {
  const spy = jest.fn();
  const node = shallow(<CreateNewButton {...props} createProcessReport={spy} />);

  await runLastEffect();

  node.find({label: 'Report'}).childAt(0).simulate('click');

  expect(spy).toHaveBeenCalled();
});

it('should call the createDashboard prop', () => {
  const spy = jest.fn();
  const node = shallow(<CreateNewButton {...props} createDashboard={spy} />);

  node.find(MenuItem).at(1).simulate('click');

  expect(spy).toHaveBeenCalled();
});
