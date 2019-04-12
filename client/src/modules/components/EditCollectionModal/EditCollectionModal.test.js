/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import EditCollectionModal from './EditCollectionModal';
import {Input} from 'components';

const collection = {
  id: 'aCollectionId',
  name: 'aCollectionName'
};

const props = {
  collection,
  onClose: jest.fn(),
  onConfirm: jest.fn()
};

it('should add isInvalid prop to the name input if name is empty', async () => {
  const node = await shallow(<EditCollectionModal {...props} />);
  await node.instance().componentDidMount();

  await node.setState({
    name: ''
  });

  expect(node.find(Input).props()).toHaveProperty('isInvalid', true);
});

it('should provide name edit input', async () => {
  const node = await shallow(<EditCollectionModal {...props} />);
  node.setState({name: 'test name'});

  expect(node.find(Input)).toBePresent();
});

it('have a cancel and save collection button', async () => {
  const node = await shallow(<EditCollectionModal {...props} />);

  expect(node.find('.confirm')).toBePresent();
  expect(node.find('.cancel')).toBePresent();
});

it('should invoke onConfirm on save button click', async () => {
  const node = await shallow(<EditCollectionModal {...props} />);

  node.find('.confirm').simulate('click');

  expect(props.onConfirm).toHaveBeenCalledWith({id: 'aCollectionId', name: 'aCollectionName'});
});

it('should disable save button if report name is empty', async () => {
  const node = await shallow(<EditCollectionModal {...props} />);
  node.setState({name: ''});

  expect(node.find('.confirm')).toBeDisabled();
});

it('should update name on input change', async () => {
  const node = await shallow(<EditCollectionModal {...props} />);
  node.setState({name: 'test name'});

  const input = 'asdf';
  node.find(Input).simulate('change', {target: {value: input}});
  expect(node.state().name).toBe(input);
});

it('should invoke onClose on cancel', async () => {
  const node = await shallow(<EditCollectionModal {...props} />);

  await node.find('.cancel').simulate('click');
  expect(props.onClose).toHaveBeenCalled();
});

it('should select the name input field when the component is mounted', async () => {
  const node = await shallow(<EditCollectionModal {...props} />);

  const input = {focus: jest.fn(), select: jest.fn()};
  node.instance().inputRef(input);

  await node.instance().componentDidMount();

  expect(input.focus).toHaveBeenCalled();
  expect(input.select).toHaveBeenCalled();
});

it('should show a loading indicator after creating or saving a collection', async () => {
  const node = await shallow(<EditCollectionModal {...props} />);
  node.find('.confirm').simulate('click');
  expect(node.find('.confirm')).toIncludeText('Saving...');
});
