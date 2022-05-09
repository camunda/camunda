/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {getVariableNames} from './service';

import FiltersEdit from './FiltersEdit';

const props = {
  availableFilters: [],
  setAvailableFilters: jest.fn(),
  reports: [{id: 'reportId'}],
  filter: [],
  setFilter: jest.fn(),
};

jest.mock('./service', () => ({
  getVariableNames: jest.fn(),
}));

beforeEach(() => {
  props.setAvailableFilters.mockClear();
  props.setFilter.mockClear();
  getVariableNames.mockClear();
});

it('should show added filters', () => {
  const node = shallow(<FiltersEdit {...props} availableFilters={[{type: 'state'}]} />);

  expect(node.find('InstanceStateFilter')).toExist();
});

it('should allow removing existing filters', () => {
  const node = shallow(<FiltersEdit {...props} availableFilters={[{type: 'state'}]} />);

  node.find('InstanceStateFilter .deleteButton').simulate('click');

  expect(props.setAvailableFilters).toHaveBeenCalledWith([]);
});

it('should allow editing variable filters', () => {
  const node = shallow(
    <FiltersEdit {...props} availableFilters={[{type: 'variable', data: {name: 'varName'}}]} />
  );

  node.find('.editButton').simulate('click');

  expect(node.find('.dashboardVariableFilter')).toExist();
  expect(node.find('.dashboardVariableFilter').prop('filterData')).toEqual({
    type: 'variable',
    data: {name: 'varName'},
  });
});

it('should include a checkbox to allow custom values', () => {
  const node = shallow(
    <FiltersEdit
      {...props}
      availableFilters={[{type: 'variable', data: {name: 'varName', type: 'String'}}]}
    />
  );

  node.find('.editButton').simulate('click');

  const postText = shallow(
    node.find('.dashboardVariableFilter').prop('getPosttext')({type: 'String'})
  );

  expect(postText.find('[type="checkbox"]')).toExist();
});
