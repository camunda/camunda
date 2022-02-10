/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {runLastEffect} from 'react';
import {shallow} from 'enzyme';

import {EntityList} from 'components';

import {Processes} from './Processes';
import {loadProcesses} from './service';

jest.mock('./service', () => ({loadProcesses: jest.fn()}));

beforeEach(() => {
  jest.clearAllMocks();
});

const props = {
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
};

it('should load processes', () => {
  loadProcesses.mockReturnValue([
    {processDefinitionKey: 'defKey', processName: 'defName', timeGoals: [], owner: 'test'},
  ]);
  const node = shallow(<Processes {...props} />);

  runLastEffect();

  expect(loadProcesses).toHaveBeenCalled();
  expect(node.find(EntityList).prop('data')).toEqual([
    {icon: 'data-source', id: 'defKey', meta: ['test', ''], name: 'defName', type: 'Process'},
  ]);
});

it('should load processes with sort parameters', () => {
  const node = shallow(<Processes {...props} />);

  node.find('EntityList').prop('onChange')('lastModifier', 'desc');

  expect(loadProcesses).toHaveBeenCalledWith('lastModifier', 'desc');
  expect(node.find('EntityList').prop('sorting')).toEqual({key: 'lastModifier', order: 'desc'});
});
