/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {shallow} from 'enzyme';

import {Button} from 'components';

import ActionItem from './ActionItem';

it('should match snapshot', () => {
  const node = shallow(<ActionItem type="ActionItem type" />);

  expect(node).toMatchSnapshot();
});

it('should render child content', () => {
  const node = shallow(<ActionItem>Some child content</ActionItem>);

  expect(node.find('div.content')).toIncludeText('Some child content');
});

it('should call the onClick handler', () => {
  const spy = jest.fn();
  const node = shallow(<ActionItem onClick={spy}>Content</ActionItem>);

  node.find(Button).simulate('click');

  expect(spy).toHaveBeenCalled();
});

it('should call the onClick handler', () => {
  const spy = jest.fn();
  const node = shallow(<ActionItem onEdit={spy}>Content</ActionItem>);

  node.find(Button).at(0).simulate('click');

  expect(spy).toHaveBeenCalled();
});

it('should show an error warning if specified', () => {
  const node = shallow(<ActionItem warning="There is an error" />);

  expect(node.find('Message')).toExist();
});

it('should prevent editing the action item if warning prop is specified', () => {
  const spy = jest.fn();
  const node = shallow(<ActionItem warning="There is an error" onEdit={spy} />);

  node.find(Button).at(0).simulate('click');
  expect(spy).not.toHaveBeenCalled();
});
