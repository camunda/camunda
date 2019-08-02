/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import EntityNameForm from './EntityNameForm';
import {Input} from 'components';
it('should provide name edit input', async () => {
  const node = await shallow(<EntityNameForm entity="Report" />);
  node.setState({name: 'test name'});

  expect(node.find(Input)).toExist();
});

it('should provide a link to view mode', async () => {
  const node = await shallow(<EntityNameForm entity="Report" />);

  expect(node.find('.save-button')).toExist();
  expect(node.find('.cancel-button')).toExist();
});

it('should invoke save on save button click', async () => {
  const spy = jest.fn();
  const node = await shallow(<EntityNameForm entity="Report" onSave={spy} />);
  node.setState({name: ''});

  node.find('.save-button').simulate('click');

  expect(spy).toHaveBeenCalled();
});

it('should disable save button if report name is empty', async () => {
  const node = await shallow(<EntityNameForm entity="Report" />);
  node.setState({name: ''});

  expect(node.find('.save-button')).toBeDisabled();
});

it('should update name on input change', async () => {
  const node = await shallow(<EntityNameForm entity="Report" />);
  node.setState({name: 'test name'});

  const input = 'asdf';
  node.find(Input).simulate('change', {target: {value: input}});
  expect(node.state().name).toBe(input);
});

it('should invoke cancel', async () => {
  const spy = jest.fn();
  const node = await shallow(<EntityNameForm entity="Report" onCancel={spy} />);

  await node.find('.cancel-button').simulate('click');
  expect(spy).toHaveBeenCalled();
});

it('should select the name input field if Report is just created', async () => {
  const node = await shallow(<EntityNameForm entity="Report" isNew />);

  const input = {focus: jest.fn(), select: jest.fn()};
  node.instance().inputRef(input);

  await node.instance().componentDidMount();

  expect(input.focus).toHaveBeenCalled();
  expect(input.select).toHaveBeenCalled();
});
