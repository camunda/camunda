/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

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

  node.find('InstanceStateFilter .DeleteButton Button').simulate('click');

  expect(props.setAvailableFilters).toHaveBeenCalledWith([]);
});

it('should allow editing variable filters', () => {
  const node = shallow(
    <FiltersEdit {...props} availableFilters={[{type: 'variable', data: {name: 'varName'}}]} />
  );

  node.find('.EditButton Button').simulate('click');

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

  node.find('.EditButton Button').simulate('click');

  const postText = node.find('.dashboardVariableFilter').prop('getPosttext')({type: 'String'});
  expect(postText.props.className).toBe('customValueCheckbox');
});

it('should disable edit button when there are no reports', () => {
  const node = shallow(
    <FiltersEdit
      {...props}
      availableFilters={[
        {
          type: 'assignee',
          data: {
            operator: 'in',
            values: ['Ourief'],
            allowCustomValues: false,
          },
        },
      ]}
      reports={[]}
    />
  );

  expect(node.find('.EditButton Button')).toBeDisabled();
});
