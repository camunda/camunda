/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {Button, Deleter} from 'components';

import {removeProcess} from './service';

import ProcessView from './ProcessView';

jest.mock('./service', () => ({
  removeProcess: jest.fn()
}));

const props = {
  id: 'processId',
  name: 'Process Name',
  xml: 'process XML',
  onDelete: jest.fn()
};

it('should match snapshot', () => {
  const node = shallow(<ProcessView {...props} />);

  expect(node).toMatchSnapshot();
});

it('should pass entity to Deleter', () => {
  const node = shallow(<ProcessView {...props} />);

  node
    .find(Button)
    .last()
    .simulate('click');

  expect(node.find(Deleter).prop('entity')).toEqual({id: 'processId', name: 'Process Name'});
});

it('should call the onDelete callback', () => {
  const node = shallow(<ProcessView {...props} />);

  node.find(Deleter).prop('onDelete')();

  expect(props.onDelete).toHaveBeenCalled();
});

it('should allow deletion of a process', () => {
  const node = shallow(<ProcessView {...props} />);

  node.find(Deleter).prop('deleteEntity')({id: '1'});

  expect(removeProcess).toHaveBeenCalledWith('1');
});
