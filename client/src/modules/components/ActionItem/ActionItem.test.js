/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {Button} from 'components';

import ActionItem from './ActionItem';

it('should have an action button', () => {
  const node = shallow(<ActionItem />);

  expect(node.find(Button)).toExist();
});

it('should render child content', () => {
  const node = shallow(<ActionItem>Some child content</ActionItem>);

  expect(node.find('span')).toIncludeText('Some child content');
});

it('should add highlighted classname when highlighted property is set', () => {
  const node = shallow(<ActionItem highlighted />);

  expect(node).toHaveClassName('highlighted');
});

it('should call the onClick handler', () => {
  const spy = jest.fn();
  const node = shallow(<ActionItem onClick={spy}>Content</ActionItem>);

  node.find(Button).simulate('click');

  expect(spy).toHaveBeenCalled();
});

it('should pass the disabled prop to the child-button', () => {
  const node = shallow(<ActionItem disabled />);

  expect(node.find(Button)).toBeDisabled();
});

it('should show an error warning if specified', () => {
  const node = shallow(<ActionItem warning="There is an error" />);

  expect(node.find('Message')).toMatchSnapshot();
});
