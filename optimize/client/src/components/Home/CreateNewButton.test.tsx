/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
  create: jest.fn(),
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
  const node = shallow(<CreateNewButton {...props} create={spy} />);

  node.find(MenuItem).at(0).simulate('click');

  expect(spy).toHaveBeenCalledWith('collection');
});

it('should call the createProcessReport prop', async () => {
  const spy = jest.fn();
  const node = shallow(<CreateNewButton {...props} create={spy} />);

  await runLastEffect();

  node.find({label: 'Report'}).childAt(0).simulate('click');

  expect(spy).toHaveBeenCalledWith('report');
});

it('should call the createDashboard prop', () => {
  const spy = jest.fn();
  const node = shallow(<CreateNewButton {...props} create={spy} />);

  node.find(MenuItem).at(1).simulate('click');

  expect(spy).toHaveBeenCalledWith('dashboard');
});

it('should call the createKpi prop', async () => {
  const spy = jest.fn();
  const node = shallow(<CreateNewButton {...props} create={spy} />);

  await runLastEffect();

  node.find({label: 'Report'}).childAt(1).simulate('click');

  expect(spy).toHaveBeenCalledWith('kpi');
});
