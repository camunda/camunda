/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import DurationFilter from './DurationFilter';

import {shallow} from 'enzyme';
import {Button, Input} from 'components';

it('should contain a modal', () => {
  const node = shallow(<DurationFilter />);

  expect(node.find('Modal')).toExist();
});

it('should contain a button to abort the filter creation', () => {
  const spy = jest.fn();
  const node = shallow(<DurationFilter close={spy} />);

  const abortButton = node.find(Button).at(0);

  abortButton.simulate('click');

  expect(spy).toHaveBeenCalled();
});

it('should have isInvalid prop on the input if value is invalid', async () => {
  const node = shallow(<DurationFilter />);
  await node.setState({
    value: 'NaN',
  });

  expect(node.find(Input).props()).toHaveProperty('isInvalid', true);
});

it('should have a create filter button', () => {
  const spy = jest.fn();
  const node = shallow(<DurationFilter addFilter={spy} />);
  const addButton = node.find('[primary]');

  addButton.simulate('click');

  expect(spy).toHaveBeenCalled();
});
