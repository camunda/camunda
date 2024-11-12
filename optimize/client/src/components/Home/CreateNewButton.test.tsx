/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';
import {MenuItem} from '@carbon/react';

import CreateNewButton from './CreateNewButton';

const props = {
  create: jest.fn(),
  importEntity: jest.fn(),
};

it('should not show the collection option if it is in a collection', () => {
  const node = shallow(<CreateNewButton {...props} />);

  expect(node.find({label: 'Collection'})).toExist();
  node.setProps({collection: '123'});
  expect(node.find({label: 'Collection'})).not.toExist();
});

it('should call the createCollection prop', () => {
  const spy = jest.fn();
  const node = shallow(<CreateNewButton {...props} create={spy} />);

  node.find(MenuItem).at(0).simulate('click');

  expect(spy).toHaveBeenCalledWith('collection');
});

it('should call the createProcessReport prop', () => {
  const spy = jest.fn();
  const node = shallow(<CreateNewButton {...props} create={spy} />);

  node.find({label: 'Report'}).simulate('click');

  expect(spy).toHaveBeenCalledWith('report');
});

it('should call the createDashboard prop', () => {
  const spy = jest.fn();
  const node = shallow(<CreateNewButton {...props} create={spy} />);

  node.find(MenuItem).at(1).simulate('click');

  expect(spy).toHaveBeenCalledWith('dashboard');
});

it('should call the createKpi prop', () => {
  const spy = jest.fn();
  const node = shallow(<CreateNewButton {...props} create={spy} />);

  node.find({label: 'Process KPI'}).simulate('click');

  expect(spy).toHaveBeenCalledWith('kpi');
});
