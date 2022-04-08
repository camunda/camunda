/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {Button} from 'components';

import ListItemAction from './ListItemAction';

it('should render a placeholder when no actions are provided', () => {
  const node = shallow(<ListItemAction />);

  expect(node).toMatchSnapshot();
});

it('should render a single button if there is only a single Action in the EntityList', () => {
  const action = jest.fn();
  const node = shallow(
    <ListItemAction actions={[{icon: 'delete', text: 'Delete', action}]} singleAction />
  );

  node.find(Button).simulate('click', {preventDefault: () => {}});

  expect(action).toHaveBeenCalled();
  expect(node).toMatchSnapshot();
});

it('should render a Dropdown per default', () => {
  const node = shallow(
    <ListItemAction actions={[{icon: 'delete', text: 'Delete', action: () => {}}]} />
  );

  expect(node).toMatchSnapshot();
});
