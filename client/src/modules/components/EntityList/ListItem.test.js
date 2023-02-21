/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import ListItem from './ListItem';

it('should match snapshot', () => {
  const node = shallow(
    <ListItem
      data={{
        type: 'Item Type',
        name: 'Test List Entry',
        icon: 'process',
        link: '/report/1',
        meta: ['Column 1', 'Column 2'],
        actions: [{action: jest.fn(), icon: 'delete', text: 'Delete Entry'}],
        warning: 'Warning Text',
      }}
      hasWarning
    />
  );

  expect(node).toMatchSnapshot();
});

it('should not render a Link component if no link is provided', () => {
  const node = shallow(<ListItem data={{}} />);

  expect(node.find('Link')).not.toExist();
});

it('should have a warning column even if no specific warning exists for this ListItem', () => {
  const node = shallow(<ListItem data={{}} hasWarning />);

  expect(node.find('.warning')).toExist();
});

it('should invoke onSelectionChange when checkbox is triggered', () => {
  const spy = jest.fn();
  const node = shallow(<ListItem data={{}} onSelectionChange={spy} />);

  node.find({type: 'checkbox'}).simulate('change', 'test');

  expect(spy).toHaveBeenCalledWith('test');
});

it('should add selectable class if there are actions and selectable prop is specfied', () => {
  const node = shallow(<ListItem data={{actions: [{}]}} selectable />);

  expect(node.find('.ListItem')).toHaveClassName('selectable');
});
