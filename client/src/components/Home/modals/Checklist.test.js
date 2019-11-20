/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import Checklist from './Checklist';
import {LabeledInput} from 'components';

const props = {
  data: [
    {id: 'id1', name: 'name 1', checked: true},
    {id: 'id2', name: 'name 2', checked: false},
    {id: 'id3', name: 'unauthorized', disabled: true}
  ],
  onChange: jest.fn(),
  selectAll: jest.fn(),
  deselectAll: jest.fn()
};

it('should match snapshot', () => {
  const node = shallow(<Checklist {...props} />);

  expect(node).toMatchSnapshot();
});

it('should invoke onChange when selecting/deselecting an item', () => {
  const node = shallow(<Checklist {...props} />);

  node
    .find(LabeledInput)
    .at(2)
    .simulate('change', {target: {checked: true}});

  expect(props.onChange).toHaveBeenCalledWith('id2', true);
});

it('should invoke enableAll/disableAll when clicking the Select all checkbox', () => {
  const node = shallow(<Checklist {...props} />);

  node
    .find(LabeledInput)
    .at(0)
    .simulate('change', {target: {checked: true}});

  expect(props.selectAll).toHaveBeenCalled();

  node
    .find(LabeledInput)
    .at(0)
    .simulate('change', {target: {checked: false}});

  expect(props.deselectAll).toHaveBeenCalled();
});

it('should hide selectAll if there is only one item', () => {
  const node = shallow(<Checklist {...props} data={[props.data[0]]} />);

  expect(node).toMatchSnapshot();
});
