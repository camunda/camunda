/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {shallow} from 'enzyme';
import {IconButton, OverflowMenuItem} from '@carbon/react';

import ListItemAction from './ListItemAction';

it('renders no actions correctly', () => {
  const node = shallow(<ListItemAction />);

  expect(node).toBeEmptyRender();
});

it('invoke overflow menu actions correctly', () => {
  const actions = [
    {icon: <span>Icon 1</span>, action: () => {}, text: 'Action 1'},
    {icon: <span>Icon 2</span>, action: jest.fn(), text: 'Action 2'},
  ];

  const node = shallow(<ListItemAction actions={actions} />);

  node.find(OverflowMenuItem).at(1).simulate('click');
  expect(actions[1]?.action).toHaveBeenCalled();
});

it('invoke icon button actions correctly', () => {
  const actions = [{icon: <span>Icon</span>, action: jest.fn(), text: 'Action'}];

  const node = shallow(<ListItemAction actions={actions} showInlineIconButtons />);

  node.find(IconButton).simulate('click');
  expect(actions[0]?.action).toHaveBeenCalled();
});
