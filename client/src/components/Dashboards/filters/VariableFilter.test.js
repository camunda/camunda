/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {runLastEffect} from 'react';
import {shallow} from 'enzyme';

import SelectionFilter from './SelectionFilter';
import DateFilter from './DateFilter';
import BooleanFilter from './BooleanFilter';
import {VariableFilter} from './VariableFilter';
import {getVariableNames} from './service';

jest.mock('./service', () => ({
  getVariableNames: jest.fn().mockReturnValue([{name: 'foo', type: 'String', label: 'fooLabel'}]),
}));

const props = {
  filter: null,
  config: {name: 'foo', type: 'String', data: {}},
  setFilter: jest.fn(),
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
};

beforeEach(() => {
  props.setFilter.mockClear();
  getVariableNames.mockClear();
});

it('should render component based on variable type', () => {
  const node = shallow(<VariableFilter {...props} />);

  expect(node.find(SelectionFilter)).toExist();
  expect(node.find(DateFilter)).not.toExist();
  expect(node.find(BooleanFilter)).not.toExist();

  node.setProps({config: {type: 'Date', data: {}}});
  expect(node.find(SelectionFilter)).not.toExist();
  expect(node.find(DateFilter)).toExist();
  expect(node.find(BooleanFilter)).not.toExist();

  node.setProps({config: {type: 'Boolean', data: {}}});
  expect(node.find(SelectionFilter)).not.toExist();
  expect(node.find(DateFilter)).not.toExist();
  expect(node.find(BooleanFilter)).toExist();
});

it('should render children', () => {
  const node = shallow(
    <VariableFilter {...props}>
      <div className="childContent" />
    </VariableFilter>
  );

  expect(node.find('.childContent')).toExist();
});

it('should load variable label and display if it exists', async () => {
  const node = shallow(<VariableFilter {...props} reports={[]} />);

  await runLastEffect();

  expect(node.find('.title')).toIncludeText('fooLabel');
});
