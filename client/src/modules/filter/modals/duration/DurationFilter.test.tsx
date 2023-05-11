/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ComponentProps} from 'react';
import {shallow} from 'enzyme';

import {Input} from 'components';

import DurationFilter from './DurationFilter';

const props: ComponentProps<typeof DurationFilter> = {
  close: jest.fn(),
  addFilter: jest.fn(),
  definitions: [],
  filterLevel: 'instance',
  filterType: 'processInstanceDuration',
};

it('should contain a modal', () => {
  const node = shallow(<DurationFilter {...props} />);

  expect(node.find('Modal')).toExist();
});

it('should contain a button to abort the filter creation', () => {
  const spy = jest.fn();
  const node = shallow(<DurationFilter {...props} close={spy} />);

  const abortButton = node.find('.cancel');

  abortButton.simulate('click');

  expect(spy).toHaveBeenCalled();
});

it('should have isInvalid prop on the input if value is invalid', async () => {
  const node = shallow(<DurationFilter {...props} />);
  await node.setState({
    value: 'NaN',
  });

  expect(node.find(Input).props()).toHaveProperty('isInvalid', true);
});

it('should have a create filter button', () => {
  const spy = jest.fn();
  const node = shallow(<DurationFilter {...props} addFilter={spy} />);
  const addButton = node.find('.confirm');

  addButton.simulate('click');

  expect(spy).toHaveBeenCalled();
});
